/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Logs out the user from the current session
 */
interface LogoutUseCase {
    /**
     * @param reason the reason for the logout performed
     * @see [LogoutReason]
     */
    suspend operator fun invoke(reason: LogoutReason)
}

internal class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val clientRepository: ClientRepository,
    private val userId: QualifiedID,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val clearClientDataUseCase: ClearClientDataUseCase,
    private val clearUserDataUseCase: ClearUserDataUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val pushTokenRepository: PushTokenRepository,
    private val globalCoroutineScope: CoroutineScope
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.

    override suspend operator fun invoke(reason: LogoutReason) {
        globalCoroutineScope.launch {
            deregisterTokenUseCase()
            logoutRepository.logout()
            sessionRepository.logout(userId = userId, reason)
            logoutRepository.onLogout(reason)

            when (reason) {
                LogoutReason.SELF_HARD_LOGOUT -> {
                    // we put this delay here to avoid a race condition when
                    // receiving web socket events at the exact time of logging put
                    delay(CLEAR_DATA_DELAY)
                    clearClientDataUseCase()
                    clearUserDataUseCase() // this clears also current client id
                }

                LogoutReason.REMOVED_CLIENT, LogoutReason.DELETED_ACCOUNT -> {
                    // we put this delay here to avoid a race condition when
                    // receiving web socket events at the exact time of logging put
                    delay(CLEAR_DATA_DELAY)
                    clearClientDataUseCase()
                    clientRepository.clearCurrentClientId()
                    clientRepository.clearHasRegisteredMLSClient()
                    // After logout we need to mark the Firebase token as invalid
                    // locally so that we can register a new one on the next login.
                    pushTokenRepository.setUpdateFirebaseTokenFlag(true)
                }

                LogoutReason.SELF_SOFT_LOGOUT, LogoutReason.SESSION_EXPIRED -> {
                    clientRepository.clearCurrentClientId()
                    // After logout we need to mark the Firebase token as invalid
                    // locally so that we can register a new one on the next login.
                    pushTokenRepository.setUpdateFirebaseTokenFlag(true)
                }
            }

            userSessionScopeProvider.get(userId)?.cancel()
            userSessionScopeProvider.delete(userId)
        }
    }

    companion object {
        const val CLEAR_DATA_DELAY = 1000L
    }
}
