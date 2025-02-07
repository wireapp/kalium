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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.UpgradePersonalToTeamApi.Companion.MIN_API_VERSION
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
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
    suspend fun getTeamUrlForUser(userId: UserId): String?
}

@Suppress("LongParameterList", "TooManyFunctions")
internal class ServerConfigDataSource(
    override val serverConfigurationDAO: ServerConfigurationDAO,
    override val versionApi: VersionApi,
    override val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper(),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ServerConfigRepository, ServerConfigRepositoryExtension(
    versionApi, serverConfigurationDAO, serverConfigMapper
) {

    override val minimumApiVersionForPersonalToTeamAccountMigration = MIN_API_VERSION

    override suspend fun getOrFetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        wrapStorageRequest { serverConfigurationDAO.configByLinks(serverConfigMapper.toEntity(serverLinks)) }.fold({
            fetchApiVersionAndStore(serverLinks)
        }, {
            Either.Right(serverConfigMapper.fromEntity(it))
        })

    override suspend fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig> =
        withContext(dispatchers.io) {
            storeServerLinksAndMetadata(links, metadata)
        }

    override suspend fun updateConfigMetaData(serverConfig: ServerConfig): Either<CoreFailure, Unit> =
        fetchMetadata(serverConfig.links)
            .flatMap { newMetaData ->
                wrapStorageRequest {
                    serverConfigurationDAO.updateServerMetaData(
                        id = serverConfig.id,
                        federation = newMetaData.federation,
                        commonApiVersion = newMetaData.commonApiVersion.version
                    )
                }
            }

    override suspend fun configForUser(userId: UserId): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest { serverConfigurationDAO.configForUser(userId.toDao()) }
            .map { serverConfigMapper.fromEntity(it) }

    override suspend fun commonApiVersion(domain: String): Either<CoreFailure, Int> = wrapStorageRequest {
        serverConfigurationDAO.getCommonApiVersion(domain)
    }

    override suspend fun getTeamUrlForUser(userId: UserId): String? = serverConfigurationDAO.teamUrlForUser(userId.toDao())
}
