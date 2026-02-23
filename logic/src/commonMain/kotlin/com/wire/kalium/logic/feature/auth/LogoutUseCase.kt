/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.notification.PushTokenRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.call.usecase.EndCallUseCase
import com.wire.kalium.logic.feature.call.usecase.ObserveEstablishedCallsUseCase
import com.wire.kalium.logic.feature.client.ClearClientDataUseCase
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.UserSessionWorkScheduler
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Logs out the user from the current session
 */
@Mockable
public interface LogoutUseCase {
    /**
     * @param reason the reason for the logout performed
     * @param waitUntilCompletes if true, the logout suspend fun will wait until all the logout operations are completed
     * @see [LogoutReason]
     */
    public suspend operator fun invoke(reason: LogoutReason, waitUntilCompletes: Boolean = false)
}

internal class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val clientRepository: ClientRepository,
    private val userConfigRepository: UserConfigRepository,
    private val userId: QualifiedID,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val clearClientDataUseCase: ClearClientDataUseCase,
    private val clearUserDataUseCase: ClearUserDataUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val pushTokenRepository: PushTokenRepository,
    private val globalCoroutineScope: CoroutineScope,
    private val userSessionWorkScheduler: UserSessionWorkScheduler,
    private val getEstablishedCallsUseCase: ObserveEstablishedCallsUseCase,
    private val endCallUseCase: EndCallUseCase,
    private val logoutCallback: LogoutCallback,
    private val kaliumConfigs: KaliumConfigs
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.

    override suspend operator fun invoke(reason: LogoutReason, waitUntilCompletes: Boolean) {
        globalCoroutineScope.launch {
            getEstablishedCallsUseCase().firstOrNull()?.forEach {
                endCallUseCase(it.conversationId)
            }

            if (reason != LogoutReason.SESSION_EXPIRED) {
                deregisterTokenUseCase()
                logoutRepository.logout()
            }

            sessionRepository.logout(userId = userId, reason)
            logoutRepository.onLogout(reason)
            userSessionWorkScheduler.cancelScheduledSendingOfPendingMessages()

            kaliumLogger.withTextTag(TAG).d("Logout reason: $reason")

            val willWipeData = reason == LogoutReason.SELF_HARD_LOGOUT ||
                (reason == LogoutReason.REMOVED_CLIENT && kaliumConfigs.wipeOnDeviceRemoval) ||
                (reason == LogoutReason.DELETED_ACCOUNT && kaliumConfigs.wipeOnDeviceRemoval) ||
                (reason == LogoutReason.SESSION_EXPIRED && kaliumConfigs.wipeOnCookieInvalid)

            // Cancel the session scope and drain all in-flight coroutines before wiping
            // the database. Without this, coroutines still holding JDBC connections to the
            // DB path can execute statements after nuke() deletes the file, causing SQLite
            // to auto-recreate an empty schema-less file at the same path. The next login
            // then opens a 0-byte DB and fails with "no such table".
            if (willWipeData) {
                userSessionScopeProvider.get(userId)?.let { scope ->
                    scope.cancel()
                    scope.coroutineContext.job.join()
                }
            }

            userConfigRepository.clearE2EISettings()

            when (reason) {
                LogoutReason.SELF_HARD_LOGOUT -> wipeAllData()
                LogoutReason.REMOVED_CLIENT, LogoutReason.DELETED_ACCOUNT -> {
                    if (kaliumConfigs.wipeOnDeviceRemoval) {
                        wipeAllData()
                    } else {
                        wipeTokenAndMetadata()
                    }
                }

                LogoutReason.SESSION_EXPIRED -> {
                    if (kaliumConfigs.wipeOnCookieInvalid) {
                        wipeAllData()
                    } else {
                        clearCurrentClientIdAndFirebaseTokenFlag()
                    }
                }

                LogoutReason.SELF_SOFT_LOGOUT -> clearCurrentClientIdAndFirebaseTokenFlag()
                LogoutReason.MIGRATION_TO_CC_FAILED -> prepareForCoreCryptoMigrationRecovery()
            }

            // Non-wipe paths: cancel the scope now (it was not cancelled early above).
            if (!willWipeData) {
                userSessionScopeProvider.get(userId)?.cancel()
            }
            userSessionScopeProvider.delete(userId)
            logoutCallback(userId, reason)
        }.let { if (waitUntilCompletes) it.join() else it }
    }

    private suspend fun prepareForCoreCryptoMigrationRecovery() {
        clearClientDataUseCase()
        logoutRepository.clearClientRelatedLocalMetadata()
        clientRepository.clearRetainedClientId()
        clientRepository.clearClientHasConsumableNotifications()
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }

    private suspend fun clearCurrentClientIdAndFirebaseTokenFlag() {
        clientRepository.clearCurrentClientId()
        clientRepository.clearNewClients()
        // After logout we need to mark the Firebase token as invalid
        // locally so that we can register a new one on the next login.
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }

    private suspend fun wipeAllData() {
        // UserSessionScope is cancelled and joined before this is called (see invoke()),
        // so no in-flight coroutines can write to the DB path after nuke() deletes the file.
        clearClientDataUseCase()
        clearUserDataUseCase() // this clears also current client id
    }

    private suspend fun wipeTokenAndMetadata() {
        // receiving web socket events at the exact time of logging put
        clearClientDataUseCase()
        logoutRepository.clearClientRelatedLocalMetadata()
        clientRepository.clearCurrentClientId()
        clientRepository.clearHasRegisteredMLSClient()
        clientRepository.clearNewClients()

        // After logout we need to mark the Firebase token as invalid
        // locally so that we can register a new one on the next login.
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)
    }

    companion object {
        const val TAG = "LogoutUseCase"
    }
}
