package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map

class IsPasswordRequiredUseCase internal constructor(
    private val selfUserId: UserId,
    private val sessionRepository: SessionRepository,
) {
    suspend operator fun invoke(): Result = eitherInvoke().fold({
        Result.Failure(it)
    }, {
        Result.Success(it)
    })

    internal suspend fun eitherInvoke(): Either<StorageFailure, Boolean> = sessionRepository.ssoId(selfUserId).map {
        it == null || it.subject != null
    }

    sealed class Result {
        data class Success(val value: Boolean) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
