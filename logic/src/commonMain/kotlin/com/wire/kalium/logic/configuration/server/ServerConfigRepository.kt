package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.configuration.ServerConfigResponse
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map


internal interface ServerConfigRepository {
    /**
     * download an on premise server configuration from a json file
     * @param serverConfigUrl url for the server configuration url
     * @return Either ServerConfigResponse or NetworkFailure
     */
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfigResponse>

    /**
     * @return list of all locally stored server configurations
     */
    fun configList(): Either<StorageFailure, List<ServerConfig>>

    /**
     * @return observable list of all locally stored server configurations
     */
    fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>>

    /**
     * delete a locally stored server configuration
     */
    fun deleteById(id: String): Either<StorageFailure, Unit>
    fun delete(serverConfig: ServerConfig): Either<StorageFailure, Unit>
    fun storeConfig(
        serverConfigResponse: ServerConfigResponse,
        domain: String?,
        apiVersion: Int,
        federation: Boolean
    ): Either<StorageFailure, ServerConfig>

    /**
     * calculate the app/server common api version for a new non stored config and store it locally if the version is valid
     * can return a ServerConfigFailure in case of an invalid version
     * @param serverConfigResponse
     * @return ServerConfigFailure in case of an invalid version
     * @return NetworkFailure in case of remote communication error
     * @return StorageFailure in case of DB errors when storing configuration
     */
    suspend fun fetchApiVersionAndStore(serverConfigResponse: ServerConfigResponse): Either<CoreFailure, ServerConfig>

    /**
     * retrieve a config from the local DB by ID
     */
    fun configById(id: String): Either<StorageFailure, ServerConfig>

    /**
     * update the api version of a locally stored config
     */
    suspend fun updateConfigApiVersion(id: String): Either<CoreFailure, Unit>
}

internal class ServerConfigDataSource(
    private val api: ServerConfigApi,
    private val dao: ServerConfigurationDAO,
    private val versionApi: VersionApi,
    private val serverConfigUtil: ServerConfigUtil,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfigResponse> = wrapApiRequest {
        api.fetchServerConfig(serverConfigUrl)
    }

    override fun configList(): Either<StorageFailure, List<ServerConfig>> =
        wrapStorageRequest { dao.allConfig() }.map { it.map(serverConfigMapper::fromEntity) }

    override fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>> =
        wrapStorageRequest { dao.allConfigFlow().map { it.map(serverConfigMapper::fromEntity) } }

    override fun deleteById(id: String) = wrapStorageRequest { dao.deleteById(id) }

    override fun delete(serverConfig: ServerConfig) = deleteById(serverConfig.id)

    override fun storeConfig(
        serverConfigResponse: ServerConfigResponse, domain: String?, apiVersion: Int, federation: Boolean
    ): Either<StorageFailure, ServerConfig> = wrapStorageRequest {
        val newId = uuid4().toString()
        with(serverConfigResponse.endpoints) {
            dao.insert(
                id = newId,
                apiBaseUrl = apiBaseUrl,
                accountBaseUrl = accountsBaseUrl,
                webSocketBaseUrl = webSocketBaseUrl,
                blackListUrl = blackListUrl,
                teamsUrl = teamsUrl,
                websiteUrl = websiteUrl,
                title = serverConfigResponse.title,
                federation = federation,
                domain = domain,
                commonApiVersion = apiVersion
            )
            newId
        }
    }.flatMap { storedConfigId ->
        wrapStorageRequest { dao.configById(storedConfigId) }
    }.map { serverConfigMapper.fromEntity(it) }

    override suspend fun fetchApiVersionAndStore(serverConfigResponse: ServerConfigResponse): Either<CoreFailure, ServerConfig> =
        wrapApiRequest { versionApi.fetchApiVersion(Url(serverConfigResponse.endpoints.apiBaseUrl)) }
            .flatMap { versionInfoDTO ->
                serverConfigUtil.calculateApiVersion(versionInfoDTO.supported)
                    .flatMap { commonApiVersion ->
                        storeConfig(serverConfigResponse, versionInfoDTO.domain, commonApiVersion, versionInfoDTO.federation)
                    }
            }


    override fun configById(id: String): Either<StorageFailure, ServerConfig> = wrapStorageRequest {
        dao.configById(id)
    }.map { serverConfigMapper.fromEntity(it) }



    override suspend fun updateConfigApiVersion(id: String): Either<CoreFailure, Unit> = configById(id)
        .flatMap { wrapApiRequest { versionApi.fetchApiVersion(Url(it.apiBaseUrl)) } }
        .flatMap { serverConfigUtil.calculateApiVersion(it.supported) }
        .flatMap { wrapStorageRequest { dao.updateApiVersion(id, it) } }
}
