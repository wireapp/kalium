package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Use Case that allows a user accept a connection request to connect with another User
 */
fun interface AcceptConnectionRequestUseCase {
    /**
     * Use case [AcceptConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [AcceptConnectionRequestUseCaseResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult
}

internal class AcceptConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : AcceptConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): AcceptConnectionRequestUseCaseResult {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.ACCEPTED)
            .fold({
                AcceptConnectionRequestUseCaseResult.Failure(it)
            }, {
                AcceptConnectionRequestUseCaseResult.Success
            })
    }
}

sealed class AcceptConnectionRequestUseCaseResult {
    object Success : AcceptConnectionRequestUseCaseResult()
    class Failure(val coreFailure: CoreFailure) : AcceptConnectionRequestUseCaseResult()
}
