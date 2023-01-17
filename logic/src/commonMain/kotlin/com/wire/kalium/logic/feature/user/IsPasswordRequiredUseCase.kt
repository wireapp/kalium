package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Checks if the current requires password to authenticate operations.
 * In case the user doesn't have a password, means is an SSO user.
 */
class IsPasswordRequiredUseCase internal constructor(
    private val selfUserId: UserId,
    private val sessionRepository: SessionRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @return [Result] with [Boolean] true if the user requires password, false otherwise.
     */
    suspend operator fun invoke(): Result =
        withContext(dispatchers.default) {
            eitherInvoke().fold({
                Result.Failure(it)
            }, {
                Result.Success(it)
            })
        }

    internal suspend fun eitherInvoke(): Either<StorageFailure, Boolean> = sessionRepository.ssoId(selfUserId).map {
        it?.subject == null
    }

    sealed class Result {
        data class Success(val value: Boolean) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
