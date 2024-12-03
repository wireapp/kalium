/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.UpgradePersonalToTeamApi.Companion.MIN_API_VERSION
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.http.Url
import kotlinx.coroutines.withContext

interface ServerConfigRepository {
    val minimumApiVersionForPersonalToTeamAccountMigration: Int

    suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig>
    suspend fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig>

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
     * update the api version and federation status of a locally stored config
     */
    suspend fun updateConfigMetaData(serverConfig: ServerConfig): Either<CoreFailure, Unit>

    /**
     * Return the server links and metadata for the given userId
     */
    suspend fun configForUser(userId: UserId): Either<StorageFailure, ServerConfig>
    suspend fun commonApiVersion(domain: String): Either<CoreFailure, Int>
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ServerConfigDataSource(
    private val dao: ServerConfigurationDAO,
    private val versionApi: VersionApi,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ServerConfigRepository {

    override val minimumApiVersionForPersonalToTeamAccountMigration = MIN_API_VERSION

    override suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        wrapStorageRequest { dao.configByLinks(serverConfigMapper.toEntity(serverLinks)) }.fold({
            fetchApiVersionAndStore(serverLinks)
        }, {
            Either.Right(serverConfigMapper.fromEntity(it))
        })

    override suspend fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig> =
        withContext(dispatchers.io) {
            wrapStorageRequest {
                // check if such config is already inserted
                val storedConfigId = dao.configByLinks(serverConfigMapper.toEntity(links))?.id
                if (storedConfigId != null) {
                    // if already exists then just update it
                    dao.updateServerMetaData(
                        id = storedConfigId,
                        federation = metadata.federation,
                        commonApiVersion = metadata.commonApiVersion.version
                    )
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
                            apiProxyHost = links.apiProxy?.host,
                            apiProxyNeedsAuthentication = links.apiProxy?.needsAuthentication,
                            apiProxyPort = links.apiProxy?.port
                        )
                    )
                    newId
                }
            }.flatMap { storedConfigId ->
                wrapStorageRequest { dao.configById(storedConfigId) }
            }.map {
                serverConfigMapper.fromEntity(it)
            }
        }

    override suspend fun fetchApiVersionAndStore(links: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        fetchMetadata(links)
            .flatMap { metaData ->
                storeConfig(links, metaData)
            }

<<<<<<< HEAD
    override suspend fun updateConfigApiVersion(serverConfig: ServerConfig): Either<CoreFailure, Unit> =
        fetchMetadata(serverConfig.links)
            .flatMap { wrapStorageRequest { dao.updateApiVersion(serverConfig.id, it.commonApiVersion.version) } }
=======
    override suspend fun updateConfigMetaData(serverConfig: ServerConfig): Either<CoreFailure, Unit> =
        fetchMetadata(serverConfig.links)
            .flatMap { newMetaData ->
                wrapStorageRequest {
                    dao.updateServerMetaData(
                        id = serverConfig.id,
                        federation = newMetaData.federation,
                        commonApiVersion = newMetaData.commonApiVersion.version
                    )
                }
            }
>>>>>>> 790b885dcf (fix: update federation flag when fetching server config [WPB-14728] (#3143))

    override suspend fun configForUser(userId: UserId): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest { dao.configForUser(userId.toDao()) }
            .map { serverConfigMapper.fromEntity(it) }

    override suspend fun commonApiVersion(domain: String): Either<CoreFailure, Int> = wrapStorageRequest {
        dao.getCommonApiVersion(domain)
    }

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
