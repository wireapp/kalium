package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.UserSessionScopeProvider
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onSuccess

/**
 * This class is responsible for deleting a user session and freeing up all the resources.
 */
class DeleteSessionUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val userSessionScopeProvider: UserSessionScopeProvider
) {
    operator fun invoke(userId: UserId) = sessionRepository.deleteSession(userId)
        .onSuccess {
            userSessionScopeProvider.delete(userId)
        }.fold({
            Result.Failure(it)
        }, {
            Result.Success
        })

    sealed class Result {
        object Success : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
