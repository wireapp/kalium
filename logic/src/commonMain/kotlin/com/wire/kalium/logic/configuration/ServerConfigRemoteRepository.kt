package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.configuration.ServerConfigApi

interface ServerConfigRemoteRepository {
    suspend fun fetchServerConfig(remoteConfigUrl: String): Either<NetworkFailure, ServerConfig>
}

class ServerConfigRemoteDataSource(
    private val remoteConfigApi: ServerConfigApi,
    private val serverConfigMapper: ServerConfigMapper
) : ServerConfigRemoteRepository {

    override suspend fun fetchServerConfig(remoteConfigUrl: String): Either<NetworkFailure, ServerConfig> = wrapApiRequest {
        remoteConfigApi.fetchServerConfig(remoteConfigUrl)
    }.map { serverConfigMapper.fromBackendConfig(it) }

}
