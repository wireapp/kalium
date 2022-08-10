package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows the current self user to unblock another previously blocked user
 */
fun interface UnblockUserUseCase {
    /**
     * Use case [UnblockUserUseCase] operation
     *
     * @param userId the target user whom to block
     * @return a [UnblockUserResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): UnblockUserResult
}

internal class UnblockUserUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : UnblockUserUseCase {

    override suspend fun invoke(userId: UserId): UnblockUserResult =
        connectionRepository.updateConnectionStatus(userId, ConnectionState.ACCEPTED)
            .fold({
                kaliumLogger.e("An error occurred when unblocking a user $userId")
                UnblockUserResult.Failure(it)
            }, {
                UnblockUserResult.Success
            })
}

sealed class UnblockUserResult {
    object Success : UnblockUserResult()
    class Failure(val coreFailure: CoreFailure) : UnblockUserResult()
}
