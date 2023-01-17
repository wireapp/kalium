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
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

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
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    override suspend operator fun invoke(reason: LogoutReason) = withContext(dispatcher.default) {
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
                clientRepository.clearHasRegisteredMLSClient()
            }

            LogoutReason.SELF_SOFT_LOGOUT, LogoutReason.SESSION_EXPIRED -> {
                clientRepository.clearCurrentClientId()
            }
        }

        // After logout we need to mark the Firebase token as invalid locally so that we can register a new one on the next login.
        pushTokenRepository.setUpdateFirebaseTokenFlag(true)

        logoutRepository.onLogout(reason)
        userSessionScopeProvider.get(userId)?.cancel()
        userSessionScopeProvider.delete(userId)
    }

    companion object {
        const val CLEAR_DATA_DELAY = 1000L
    }
}
