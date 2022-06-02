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
fun interface IgnoreConnectionRequestUseCase {
    /**
     * Use case [IgnoreConnectionRequestUseCase] operation
     *
     * @param userId the target user to ignore with
     * @return a [IgnoreConnectionRequestUseCaseResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): IgnoreConnectionRequestUseCaseResult
}

internal class IgnoreConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : IgnoreConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): IgnoreConnectionRequestUseCaseResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.IGNORED)
            .fold({
                kaliumLogger.e("An error occurred when ignoring the connection request to $userId")
                IgnoreConnectionRequestUseCaseResult.Failure(it)
            }, {
                IgnoreConnectionRequestUseCaseResult.Success
            })
    }
}

sealed class IgnoreConnectionRequestUseCaseResult {
    object Success : IgnoreConnectionRequestUseCaseResult()
    class Failure(val coreFailure: CoreFailure) : IgnoreConnectionRequestUseCaseResult()
}
