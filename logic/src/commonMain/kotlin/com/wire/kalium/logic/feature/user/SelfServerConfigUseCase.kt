package com.wire.kalium.logic.feature.user

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class SelfServerConfigUseCase internal constructor(
    private val selfUserId: UserId,
    private val serverConfigRepository: ServerConfigRepository
) {
    suspend operator fun invoke(): Result =
        serverConfigRepository.configForUser(selfUserId).fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })

    sealed class Result {
        data class Success(val serverLinks: ServerConfig) : Result()
        data class Failure(val cause: CoreFailure) : Result()
    }
}
