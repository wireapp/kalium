package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

/**
 * Gets the server configuration for the given user.
 */
class ServerConfigForAccountUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository
) {
    /**
     * @param userId the id of the user
     * @return the [ServerConfig] for the given user if successful, otherwise a [StorageFailure]
     */
    suspend operator fun invoke(userId: UserId) =
        serverConfigRepository.configForUser(userId)
            .fold(Result::Failure, Result::Success)

    sealed class Result {
        data class Success(val config: ServerConfig) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
