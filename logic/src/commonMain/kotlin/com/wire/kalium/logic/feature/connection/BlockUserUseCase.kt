package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user to ignore a connection request from given user
 */
fun interface BlockUserUseCase {
    /**
     * Use case [BlockUserUseCase] operation
     *
     * @param userId the target user whom to block
     * @return a [BlockUserResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): BlockUserResult
}

internal class BlockUserUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : BlockUserUseCase {

    override suspend fun invoke(userId: UserId): BlockUserResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.BLOCKED)
            .fold({
                kaliumLogger.e("An error occurred when blocking a user $userId")
                BlockUserResult.Failure(it)
            }, {
                BlockUserResult.Success
            })
    }
}

sealed class BlockUserResult {
    object Success : BlockUserResult()
    class Failure(val coreFailure: CoreFailure) : BlockUserResult()
}
