package com.wire.kalium.logic.feature.server

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class ServerConfigForAccountUseCase internal constructor(
    private val serverConfigRepository: ServerConfigRepository
) {
    suspend operator fun invoke(userId: UserId) =
        serverConfigRepository.configForUser(userId)
            .fold(Result::Failure, Result::Success)

    sealed class Result {
        data class Success(val config: ServerConfig) : Result()
        data class Failure(val cause: StorageFailure) : Result()
    }
}
