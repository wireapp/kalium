package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapLeft
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.kaliumLogger

/**
 * Use Case that allows a user to block user
 */
fun interface UnblockUserUseCase {
    /**
     * Use case [UnblockUserUseCase] operation
     *
     * @param userId the target user whom to unblock
     * @return a [Either] indicating the operation result:
     * Left - [CoreFailure] if occurred,
     * Right - [Unit] is success
     */
    suspend operator fun invoke(userId: UserId): Either<CoreFailure, Unit>
}

internal class UnblockUserUseCaseImpl(
    private val connectionRepository: ConnectionRepository
) : UnblockUserUseCase {

    override suspend fun invoke(userId: UserId): Either<CoreFailure, Unit> {
        return connectionRepository.updateConnectionStatus(userId, ConnectionState.ACCEPTED)
            .onFailure { kaliumLogger.e("An error occurred when unblocking a user $userId") }
            .map { Unit }
    }
}
