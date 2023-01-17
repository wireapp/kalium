package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case will change the current session for the given user id.
 * ie: Use this use case to switch between users.
 *
 * @see [UpdateCurrentSessionUseCase.Result]
 */
class UpdateCurrentSessionUseCase internal constructor(
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    suspend operator fun invoke(userId: UserId?) = withContext(dispatchers.default) {
        sessionRepository.updateCurrentSession(userId).fold({ Result.Failure(it) }, { Result.Success })
    }

    sealed class Result {
        object Success : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
