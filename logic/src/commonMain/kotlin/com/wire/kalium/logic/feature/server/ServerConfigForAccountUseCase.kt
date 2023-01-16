package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Gets the server configuration for the given user.
 */
class ServerConfigForAccountUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {
    /**
     * @param userId the id of the user
     * @return the [ServerConfig] for the given user if successful, otherwise a [StorageFailure]
     */
    suspend operator fun invoke(userId: UserId) =
        withContext(dispatchers.default) {
            serverConfigRepository.configForUser(userId)
                .fold(Result::Failure, Result::Success)
        }

    sealed class Result {
        data class Success(val config: ServerConfig) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
