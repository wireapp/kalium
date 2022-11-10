package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
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
    fun storeConfig(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): Either<StorageFailure, ServerConfig>

    /**
     * calculate the app/server common api version for a new non stored config and store it locally if the version is valid
     * can return a ServerConfigFailure in case of an invalid version
     * @param links
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

    /**
     * Return the server links and metadata for the given userId
     */
    suspend fun configForUser(userId: UserId): Either<StorageFailure, ServerConfig>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ServerConfigDataSource(
    private val api: ServerConfigApi,
    private val dao: ServerConfigurationDAO,
    private val versionApi: VersionApi,
    private val developmentApiEnabled: Boolean,
    private val backendMetaDataUtil: BackendMetaDataUtil = BackendMetaDataUtilImpl,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : ServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig.Links> =
        wrapApiRequest { api.fetchServerConfig(serverConfigUrl) }
            .map { serverConfigMapper.fromDTO(it) }

    override fun configList(): Either<StorageFailure, List<ServerConfig>> =
        wrapStorageRequest { dao.allConfig() }.map { it.map(serverConfigMapper::fromEntity) }

    override fun configFlow(): Either<StorageFailure, Flow<List<ServerConfig>>> =
        wrapStorageRequest { dao.allConfigFlow().map { it.map(serverConfigMapper::fromEntity) } }

    override fun deleteById(id: String) = wrapStorageRequest { dao.deleteById(id) }

    override fun delete(serverConfig: ServerConfig) = deleteById(serverConfig.id)
    override suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        wrapStorageRequest { dao.configByLinks(serverConfigMapper.toEntity(serverLinks)) }.fold({
            fetchApiVersionAndStore(serverLinks)
        }, {
            Either.Right(serverConfigMapper.fromEntity(it))
        })

    override fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest {
            // check if such config is already inserted
            val storedConfigId = dao.configByLinks(serverConfigMapper.toEntity(links))?.id
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
                        isOnPremises = links.isOnPremises,
                        title = links.title,
                        federation = metadata.federation,
                        domain = metadata.domain,
                        commonApiVersion = metadata.commonApiVersion.version,
                        proxyApi = links.proxy?.proxyApi,
                        proxyNeedsAuthentication = links.proxy?.needsAuthentication,
                        proxyPort = links.proxy?.proxyPort
                    )
                )
                newId
            }
        }.flatMap { storedConfigId ->
            wrapStorageRequest { dao.configById(storedConfigId) }
        }.map {
            serverConfigMapper.fromEntity(it)
        }

    override fun storeConfig(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): Either<StorageFailure, ServerConfig> {
        val metaDataDTO = backendMetaDataUtil.calculateApiVersion(
            versionInfoDTO = VersionInfoDTO(
                developmentSupported = versionInfo.developmentSupported,
                domain = versionInfo.domain,
                federation = versionInfo.federation,
                supported = versionInfo.supported,
            ),
            developmentApiEnabled = developmentApiEnabled
        )
        return storeConfig(links, serverConfigMapper.fromDTO(metaDataDTO))
    }

    override suspend fun fetchApiVersionAndStore(links: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        fetchMetadata(links)
            .flatMap { metaData ->
                storeConfig(links, metaData)
            }

    override fun configById(id: String): Either<StorageFailure, ServerConfig> = wrapStorageRequest {
        dao.configById(id)
    }.map { serverConfigMapper.fromEntity(it) }

    override fun configByLinks(links: ServerConfig.Links): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest { dao.configByLinks(serverConfigMapper.toEntity(links)) }.map { serverConfigMapper.fromEntity(it) }

    override suspend fun updateConfigApiVersion(id: String): Either<CoreFailure, Unit> = configById(id)
        .flatMap { fetchMetadata(it.links) }
        .flatMap { wrapStorageRequest { dao.updateApiVersion(id, it.commonApiVersion.version) } }

    override suspend fun configForUser(userId: UserId): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest { dao.configForUser(idMapper.toDaoModel(userId)) }
            .map { serverConfigMapper.fromEntity(it) }

    private suspend fun fetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig.MetaData> =
        wrapApiRequest { versionApi.fetchApiVersion(Url(serverLinks.api)) }
            .flatMap {
                when (it.commonApiVersion) {
                    ApiVersionDTO.Invalid.New -> Either.Left(ServerConfigFailure.NewServerVersion)
                    ApiVersionDTO.Invalid.Unknown -> Either.Left(ServerConfigFailure.UnknownServerVersion)
                    is ApiVersionDTO.Valid -> Either.Right(it)
                }
            }.map { serverConfigMapper.fromDTO(it) }
}
