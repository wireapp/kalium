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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay

interface LogoutUseCase {
    suspend operator fun invoke(reason: LogoutReason)
}

class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val clientRepository: ClientRepository,
    private val userId: QualifiedID,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val clearClientDataUseCase: ClearClientDataUseCase,
    private val clearUserDataUseCase: ClearUserDataUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider,
    private val pushTokenRepository: PushTokenRepository
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    override suspend operator fun invoke(reason: LogoutReason) {
        logoutRepository.onLogout(reason)
        deregisterTokenUseCase()
        logoutRepository.logout()
        sessionRepository.logout(userId = userId, reason)

        when (reason) {
            LogoutReason.SELF_HARD_LOGOUT -> {
                // we put this delay here to avoid a race condition when receiving web socket events at the exact time of logging put
                delay(CLEAR_DATA_DELAY)
                clearClientDataUseCase()
                clearUserDataUseCase() // this clears also current client id
            }
            LogoutReason.REMOVED_CLIENT, LogoutReason.DELETED_ACCOUNT -> {
                // we put this delay here to avoid a race condition when receiving web socket events at the exact time of logging put
                delay(CLEAR_DATA_DELAY)
                clearClientDataUseCase()
                clientRepository.clearCurrentClientId()
            }
            LogoutReason.SELF_SOFT_LOGOUT, LogoutReason.SESSION_EXPIRED -> {
                clientRepository.clearCurrentClientId()
            }
        }

        // After logout we need to mark the Firebase token as invalid locally so that we can register a new one on the next login.
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)

        userSessionScopeProvider.get(userId)?.cancel()
        userSessionScopeProvider.delete(userId)
    }

    companion object {
        const val CLEAR_DATA_DELAY = 1000L
    }
}
