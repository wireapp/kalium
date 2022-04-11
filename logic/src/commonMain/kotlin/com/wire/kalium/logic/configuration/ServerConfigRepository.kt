package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


interface ServerConfigRepository {
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig>

    fun configList(): Either<StorageFailure, List<ServerConfig>>
    fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>>
    fun deleteByTitle(title: String): Either<StorageFailure, Unit>
    fun delete(serverConfig: ServerConfig): Either<StorageFailure, Unit>
    fun storeConfig(serverConfig: ServerConfig): Either<StorageFailure, Unit>
}

class ServerConfigDataSource(
    private val remoteRepository: ServerConfigRemoteRepository,
    private val dao: ServerConfigurationDAO,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig> {
        return remoteRepository.fetchServerConfig(serverConfigUrl)
    }

    override fun configList(): Either<StorageFailure, List<ServerConfig>> =
        wrapStorageRequest { dao.allConfig() }.map { it.map(serverConfigMapper::fromEntity) }

    override fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>> =
        wrapStorageRequest { dao.allConfigFlow().map { it.map(serverConfigMapper::fromEntity) } }

    override fun deleteByTitle(title: String) = wrapStorageRequest { dao.deleteByTitle(title) }

    override fun delete(serverConfig: ServerConfig) = deleteByTitle(serverConfig.title)
    override fun storeConfig(serverConfig: ServerConfig): Either<StorageFailure, Unit> = wrapStorageRequest {
        with(serverConfig) {
            dao.insert(
                apiBaseUrl = apiBaseUrl,
                accountBaseUrl = accountsBaseUrl,
                webSocketBaseUrl = webSocketBaseUrl,
                blackListUrl = blackListUrl,
                teamsUrl = teamsUrl,
                websiteUrl = websiteUrl,
                title = title
            )
        }
    }
}
