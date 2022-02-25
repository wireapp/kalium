package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.utils.isSuccessful

interface ServerConfigRemoteRepository {
    suspend fun fetchServerConfig(remoteConfigUrl: String): Either<CoreFailure, ServerConfig>
}
class ServerConfigRemoteDataSource(
    private val remoteConfigApi: ServerConfigApi,
    private val serverConfigMapper: ServerConfigMapper
) : ServerConfigRemoteRepository {
    override suspend fun fetchServerConfig(remoteConfigUrl: String): Either<CoreFailure, ServerConfig> {
        val response = remoteConfigApi.fetchServerConfig(remoteConfigUrl)
        return if (response.isSuccessful())
            Either.Right(serverConfigMapper.fromBackendConfig(response.value))
        else
            Either.Left(CoreFailure.Unknown(response.kException))
    }
}
