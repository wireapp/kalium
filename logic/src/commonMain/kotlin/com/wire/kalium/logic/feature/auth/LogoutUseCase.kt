package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.logout.LogoutRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.feature.session.DeregisterTokenUseCase
import com.wire.kalium.logic.functional.onSuccess

interface LogoutUseCase {
    suspend operator fun invoke(reason: LogoutReason = LogoutReason.SELF_LOGOUT, isHardLogout: Boolean = false): UserId?
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
    override suspend operator fun invoke(reason: LogoutReason, isHardLogout: Boolean): UserId? {
        deregisterTokenUseCase()
        logoutRepository.logout()
        logout(reason, isHardLogout)
        if (isHardLogout) {
            clearUserDataUseCase()
        }
        val updatedUserId = updateCurrentSession()
        clearInMemoryUserSession()
        return updatedUserId
    }

    private fun clearInMemoryUserSession() {
        userSessionScopeProvider.delete(userId)
    }

    /**
     * Updates the current session to let all subscribers know that the session data changed.
     * If there is other valid session after logout then switch to the first found valid session, otherwise update current invalid session.
     * @return userId of the session that was switched to, if no other valid session then null
     */
    private fun updateCurrentSession(): UserId? {
        sessionRepository.allValidSessions().onSuccess {
            val updatedUserId = it.first().session.userId
            // there is another valid session so switch to that one
            sessionRepository.updateCurrentSession(updatedUserId)
            return updatedUserId
        }
        // there is no other valid session so update current one
        sessionRepository.updateCurrentSession(userId)
        return null
    }

    private fun logout(reason: LogoutReason, isHardLogout: Boolean) =
        sessionRepository.logout(userId = userId, reason, isHardLogout)
}
