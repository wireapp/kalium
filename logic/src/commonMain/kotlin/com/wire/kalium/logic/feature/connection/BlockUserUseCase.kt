package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Use Case that allows a user to block user
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
    private val connectionRepository: ConnectionRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : BlockUserUseCase {

    override suspend fun invoke(userId: UserId): BlockUserResult = withContext(dispatcher.default) {
        connectionRepository.updateConnectionStatus(userId, ConnectionState.BLOCKED)
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
