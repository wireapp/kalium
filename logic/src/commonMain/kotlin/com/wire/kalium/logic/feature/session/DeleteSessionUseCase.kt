package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider

/**
 * This class is responsible for deleting a user session and freeing up all the resources.
 */
class DeleteSessionUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider
) {
    operator fun invoke(userId: UserId) {
        sessionRepository.deleteSession(userId)
        userSessionScopeProvider.delete(userId)
    }
}
