package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either


interface ServerConfigRepository {
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<CoreFailure, ServerConfig>
}

class ServerConfigSource(
    private val remoteRepository: ServerConfigRemoteRepository
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<CoreFailure, ServerConfig> {
        return remoteRepository.fetchServerConfig(serverConfigUrl)
    }
}
