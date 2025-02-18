/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.failure.ServerConfigFailure
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import io.ktor.http.Url

/**
 * Common operations for the server configuration repository.
 */
internal abstract class ServerConfigRepositoryExtension(
    open val versionApi: VersionApi,
    open val serverConfigurationDAO: ServerConfigurationDAO,
    open val serverConfigMapper: ServerConfigMapper,
) {

    suspend fun storeServerLinksAndMetadata(
        links: ServerConfig.Links,
        metadata: ServerConfig.MetaData
    ): Either<StorageFailure, ServerConfig> =
        wrapStorageRequest {
            // check if such config is already inserted
            val storedConfigId = serverConfigurationDAO.configByLinks(serverConfigMapper.toEntity(links))?.id
            if (storedConfigId != null) {
                // if already exists then just update it
                serverConfigurationDAO.updateServerMetaData(
                    id = storedConfigId,
                    federation = metadata.federation,
                    commonApiVersion = metadata.commonApiVersion.version
                )
                if (metadata.federation) serverConfigurationDAO.setFederationToTrue(storedConfigId)
                storedConfigId
            } else {
                // otherwise insert new config
                val newId = uuid4().toString()
                serverConfigurationDAO.insert(
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
            wrapStorageRequest { serverConfigurationDAO.configById(storedConfigId) }
        }.map {
            serverConfigMapper.fromEntity(it)
        }

    suspend fun fetchMetadata(serverLinks: ServerConfig.Links): Either<CoreFailure, ServerConfig.MetaData> =
        wrapApiRequest { versionApi.fetchApiVersion(Url(serverLinks.api)) }
            .flatMap {
                when (it.commonApiVersion) {
                    ApiVersionDTO.Invalid.New -> Either.Left(ServerConfigFailure.NewServerVersion)
                    ApiVersionDTO.Invalid.Unknown -> Either.Left(ServerConfigFailure.UnknownServerVersion)
                    is ApiVersionDTO.Valid -> Either.Right(it)
                }
            }.map { serverConfigMapper.fromDTO(it) }

    suspend fun fetchApiVersionAndStore(links: ServerConfig.Links): Either<CoreFailure, ServerConfig> =
        fetchMetadata(links)
            .flatMap { metaData ->
                storeServerLinksAndMetadata(links, metaData)
            }
}
