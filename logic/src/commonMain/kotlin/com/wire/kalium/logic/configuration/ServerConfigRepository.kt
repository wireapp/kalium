package com.wire.kalium.logic.configuration

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


interface ServerConfigRepository {
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfigDTO>

    fun configList(): Either<StorageFailure, List<ServerConfig>>
    fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>>
    fun deleteByTitle(title: String): Either<StorageFailure, Unit>
    fun delete(serverConfig: ServerConfig): Either<StorageFailure, Unit>
    fun storeConfig(serverConfigDTO: ServerConfigDTO): Either<StorageFailure, ServerConfig>
}

class ServerConfigDataSource(
    private val api: ServerConfigApi,
    private val dao: ServerConfigurationDAO,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfigDTO> = wrapApiRequest {
        api.fetchServerConfig(serverConfigUrl)
    }

    override fun configList(): Either<StorageFailure, List<ServerConfig>> =
        wrapStorageRequest { dao.allConfig() }.map { it.map(serverConfigMapper::fromEntity) }

    override fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>> =
        wrapStorageRequest { dao.allConfigFlow().map { it.map(serverConfigMapper::fromEntity) } }

    override fun deleteByTitle(title: String) = wrapStorageRequest { dao.deleteById(title) }

    override fun delete(serverConfig: ServerConfig) = deleteByTitle(serverConfig.title)
    override fun storeConfig(serverConfigDTO: ServerConfigDTO): Either<StorageFailure, ServerConfig> = wrapStorageRequest<String> {
        val newId = uuid4().toString()
        with(serverConfigDTO) {
            dao.insert(
                id = newId,
                apiBaseUrl = apiBaseUrl.toString(),
                accountBaseUrl = accountsBaseUrl.toString(),
                webSocketBaseUrl = webSocketBaseUrl.toString(),
                blackListUrl = blackListUrl.toString(),
                teamsUrl = teamsUrl.toString(),
                websiteUrl = websiteUrl.toString(),
                title = title
            )
            newId
        }
    }.flatMap { storedConfigId ->
        wrapStorageRequest { dao.configById(storedConfigId) }
    }.map { serverConfigMapper.fromEntity(it) }
}
