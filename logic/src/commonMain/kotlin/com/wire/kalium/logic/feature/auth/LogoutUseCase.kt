package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.onSuccess

interface LogoutUseCase {
    suspend operator fun invoke(reason: LogoutReason = LogoutReason.SELF_LOGOUT, isHardLogout: Boolean = false)
}

class LogoutUseCaseImpl @Suppress("LongParameterList") constructor(
    private val logoutRepository: LogoutRepository,
    private val sessionRepository: SessionRepository,
    private val userId: QualifiedID,
    private val deregisterTokenUseCase: DeregisterTokenUseCase,
    private val clearUserDataUseCase: ClearUserDataUseCase,
    private val userSessionScopeProvider: UserSessionScopeProvider
) : LogoutUseCase {
    // TODO(refactor): Maybe we can simplify by taking some of the responsibility away from here.
    //                 Perhaps [UserSessionScope] (or another specialised class) can observe
    //                 the [LogoutRepository.observeLogout] and invalidating everything in [CoreLogic] level.
    override suspend operator fun invoke(reason: LogoutReason, isHardLogout: Boolean) {
        deregisterTokenUseCase()
        logoutRepository.logout()
        logout(reason, isHardLogout)
        if (isHardLogout) {
            clearUserDataUseCase()
        }
        updateCurrentSession()
        clearInMemoryUserSession()
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

    private fun updateCurrentSession() {
        sessionRepository.allValidSessions().onSuccess {
            sessionRepository.updateCurrentSession(it.first().session.userId)
        }
    }

    private fun logout(reason: LogoutReason, isHardLogout: Boolean) =
        sessionRepository.logout(userId = userId, reason, isHardLogout)
}
