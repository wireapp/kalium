package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.configuration.ServerConfigApi
import com.wire.kalium.network.api.versioning.VersionApi
import com.wire.kalium.persistence.dao_kalium_db.ServerConfigurationDAO
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal interface ServerConfigRepository {
    /**
     * download an on premise server configuration from a json file
     * @param serverConfigUrl url for the server configuration url
     * @return Either ServerConfigResponse or NetworkFailure
     */
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig.Links>

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
    suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig>
    fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig>

    /**
     * calculate the app/server common api version for a new non stored config and store it locally if the version is valid
     * can return a ServerConfigFailure in case of an invalid version
     * @param serverConfigResponse
     * @return ServerConfigFailure in case of an invalid version
     * @return NetworkFailure in case of remote communication error
     * @return StorageFailure in case of DB errors when storing configuration
     */
    suspend fun fetchApiVersionAndStore(links: ServerConfig.Links): Either<CoreFailure, ServerConfig>

    /**
     * retrieve a config from the local DB by ID
     */
    fun configById(id: String): Either<StorageFailure, ServerConfig>

    /**
     * retrieve a config from the local DB by Links
     */
    fun configByLinks(links: ServerConfig.Links): Either<StorageFailure, ServerConfig>

    /**
     * update the api version of a locally stored config
     */
    suspend fun updateConfigApiVersion(id: String): Either<CoreFailure, Unit>
}

internal class ServerConfigDataSource(
    private val api: ServerConfigApi,
    private val dao: ServerConfigurationDAO,
    private val versionApi: VersionApi,
    private val kaliumPreferences: KaliumPreferences,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig.Links> = wrapApiRequest {
        api.fetchServerConfig(serverConfigUrl)
    }.map { serverConfigMapper.fromDTO(it) }

    override fun configList(): Either<StorageFailure, List<ServerConfig>> =
        wrapStorageRequest { dao.allConfig() }.map { it.map(serverConfigMapper::fromEntity) }

    override fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>> =
        wrapStorageRequest { dao.allConfigFlow().map { it.map(serverConfigMapper::fromEntity) } }

    override fun deleteById(id: String) = wrapStorageRequest { dao.deleteById(id) }

    override fun delete(serverConfig: ServerConfig) = deleteById(serverConfig.id)
    override suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        wrapStorageRequest { dao.configByLinks(serverLinks.title, serverLinks.api, serverLinks.webSocket) }.fold({
            fetchApiVersionAndStore(serverLinks)
        }, {
            Either.Right(serverConfigMapper.fromEntity(it))
        })

    override fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest {
            // check if such config is already inserted
            val storedConfigId = dao.configByLinks(links.title, links.api, links.webSocket)?.id
            if (storedConfigId != null) {
                // if already exists then just update it
                dao.updateApiVersion(storedConfigId, metadata.commonApiVersion.version)
                if (metadata.federation) dao.setFederationToTrue(storedConfigId)
                storedConfigId
            } else {
                // otherwise insert new config
                val newId = uuid4().toString()
                dao.insert(
                    ServerConfigurationDAO.InsertData(
                        id = newId,
                        apiBaseUrl = links.api,
                        accountBaseUrl = links.accounts,
                        webSocketBaseUrl = links.webSocket,
                        blackListUrl = links.blackList,
                        teamsUrl = links.teams,
                        websiteUrl = links.website,
                        title = links.title,
                        federation = metadata.federation,
                        domain = metadata.domain,
                        commonApiVersion = metadata.commonApiVersion.version
                    )
                )
                newId
            }
        }.flatMap { storedConfigId ->
            wrapStorageRequest { dao.configById(storedConfigId) }
        }.map {
            serverConfigMapper.fromEntity(it)
        }.also {
            kaliumPreferences.putBoolean(FEDERATION_ENABLED, metadata.federation)
            kaliumPreferences.putString(CURRENT_DOMAIN, metadata.domain)
        }

    override suspend fun fetchApiVersionAndStore(links: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        wrapApiRequest { versionApi.fetchApiVersion(Url(links.api)) }
            .flatMap { metaData ->
                storeConfig(links, serverConfigMapper.fromDTO(metaData))
            }

    override fun configById(id: String): Either<StorageFailure, ServerConfig> = wrapStorageRequest {
        dao.configById(id)
    }.map { serverConfigMapper.fromEntity(it) }

    override fun configByLinks(links: ServerConfig.Links): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest { dao.configByLinks(links.title, links.api, links.webSocket) }.map { serverConfigMapper.fromEntity(it) }

    override suspend fun updateConfigApiVersion(id: String): Either<CoreFailure, Unit> = configById(id)
        .flatMap { wrapApiRequest { versionApi.fetchApiVersion(Url(it.links.api)) } }
        .flatMap { wrapStorageRequest { dao.updateApiVersion(id, it.commonApiVersion.version) } }
}

const val FEDERATION_ENABLED = "federation_enabled"
const val CURRENT_DOMAIN = "current_domain"
