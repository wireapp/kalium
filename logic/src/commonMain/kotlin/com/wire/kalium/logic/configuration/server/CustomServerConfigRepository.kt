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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.BackendMetaDataUtil
import com.wire.kalium.network.BackendMetaDataUtilImpl
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionInfoDTO
import com.wire.kalium.persistence.daokaliumdb.ServerConfigurationDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository for the fetching and storing wire server configurations
 * If you are looking for doing operations related to API version or blacklisted then use [ServerConfigRepository]
 */
internal interface CustomServerConfigRepository {
    /**
     * download an on premise server configuration from a json file
     * @param serverConfigUrl url for the server configuration url
     * @return Either ServerConfigResponse or NetworkFailure
     */
    suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig.Links>
    suspend fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig>

    suspend fun storeConfig(links: ServerConfig.Links, versionInfo: ServerConfig.VersionInfo): Either<StorageFailure, ServerConfig>

    /**
     * @return the list of [ServerConfigWithUserId] that were checked "if app needs to be updated" after the date
     */
    suspend fun getServerConfigsWithUserIdAfterTheDate(date: String): Either<StorageFailure, Flow<List<ServerConfigWithUserId>>>

    /**
     * updates lastBlackListCheckDate for the Set of configIds
     */
    suspend fun updateAppBlackListCheckDate(configIds: Set<String>, date: String)
}

internal class CustomServerConfigDataSource internal constructor(
    private val api: ServerConfigApi,
    private val developmentApiEnabled: Boolean,
    private val serverConfigurationDAO: ServerConfigurationDAO,
    private val backendMetaDataUtil: BackendMetaDataUtil = BackendMetaDataUtilImpl,
    private val serverConfigMapper: ServerConfigMapper = MapperProvider.serverConfigMapper()
) : CustomServerConfigRepository {

    override suspend fun fetchRemoteConfig(serverConfigUrl: String): Either<NetworkFailure, ServerConfig.Links> =
        wrapApiRequest { api.fetchServerConfig(serverConfigUrl) }
            .map { serverConfigMapper.fromDTO(it) }

    override suspend fun storeConfig(
        links: ServerConfig.Links,
        versionInfo: ServerConfig.VersionInfo
    ): Either<StorageFailure, ServerConfig> {
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

    override suspend fun storeConfig(links: ServerConfig.Links, metadata: ServerConfig.MetaData): Either<StorageFailure, ServerConfig> =
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

    override suspend fun getServerConfigsWithUserIdAfterTheDate(date: String): Either<StorageFailure, Flow<List<ServerConfigWithUserId>>> =
        wrapStorageRequest { serverConfigurationDAO.getServerConfigsWithAccIdWithLastCheckBeforeDate(date) }
            .map { it.map { list -> list.map(serverConfigMapper::fromEntity) } }

    override suspend fun updateAppBlackListCheckDate(configIds: Set<String>, date: String) {
        wrapStorageRequest { serverConfigurationDAO.updateBlackListCheckDate(configIds, date) }
    }

}
