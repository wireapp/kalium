package com.wire.kalium.logic.feature.session

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class UpdateCurrentSessionUseCase internal constructor(private val sessionRepository: SessionRepository) {
    suspend operator fun invoke(userId: UserId) =
        sessionRepository.updateCurrentSession(userId).fold({ Result.Failure(it) }, { Result.Success })

    sealed class Result {
        object Success : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
