package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user send a connection request to connect with another User
 */
fun interface SendConnectionRequestUseCase {
    /**
     * Use case [SendConnectionRequestUseCase] operation
     *
     * @param userId the target user to connect with
     * @return a [SendConnectionRequestResult] indicating the operation result
     */
    suspend operator fun invoke(userId: UserId): SendConnectionRequestResult
}

internal class SendConnectionRequestUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : SendConnectionRequestUseCase {

    override suspend fun invoke(userId: UserId): SendConnectionRequestResult {
        return connectionRepository.sendUserConnection(userId)
            .fold({ coreFailure ->
                kaliumLogger.e("An error occurred when sending a connection request to $userId")
                SendConnectionRequestResult.Failure(coreFailure)
            }, {
                connectionRepository.fetchSelfUserConnections()
                SendConnectionRequestResult.Success
            })
    }
}

sealed class SendConnectionRequestResult {
    object Success : SendConnectionRequestResult()
    class Failure(val coreFailure: CoreFailure) : SendConnectionRequestResult()
}
