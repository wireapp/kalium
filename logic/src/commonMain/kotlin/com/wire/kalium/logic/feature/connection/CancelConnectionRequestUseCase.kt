package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user to cancel a [ConnectionState.PENDING] connection request to connect with another user
 * before it's [ConnectionState.ACCEPTED]
 *
 */
fun interface CancelConnectionRequestUseCase {
    /**
     * Use case [CancelConnectionRequestUseCase] operation
     *
     * @param userId the target user with whom to cancel the connection request
     * @return a [CancelConnectionRequestUseCaseResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): CancelConnectionRequestUseCaseResult
}

internal class CancelConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : CancelConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): CancelConnectionRequestUseCaseResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.CANCELLED)
            .fold({
                kaliumLogger.e("An error occurred when cancelling the connection request to $userId")
                CancelConnectionRequestUseCaseResult.Failure(it)
            }, {
                CancelConnectionRequestUseCaseResult.Success
            })
    }
}

sealed class CancelConnectionRequestUseCaseResult {
    object Success : CancelConnectionRequestUseCaseResult()
    class Failure(val coreFailure: CoreFailure) : CancelConnectionRequestUseCaseResult()
}
