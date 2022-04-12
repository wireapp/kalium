package com.wire.kalium.logic.configuration

/*
interface ServerConfigRemoteRepository {
    suspend fun fetchServerConfig(remoteConfigUrl: String): Either<NetworkFailure, ServerConfig>
}

class ServerConfigRemoteDataSource(
    private val remoteConfigApi: ServerConfigApi,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerConfigRemoteRepository {

    override suspend fun fetchServerConfig(remoteConfigUrl: String): Either<NetworkFailure, ServerConfig> = wrapApiRequest {
        remoteConfigApi.fetchServerConfig(remoteConfigUrl)
    }.map { serverConfigMapper.fromDTO(it) }

}
*/
