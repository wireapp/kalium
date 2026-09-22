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

@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.configuration.server

import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.ServerConfigWithUserIdEntity

interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links
    fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO
    fun toDTO(apiProxy: ServerConfig.ApiProxy): ServerConfigDTO.ApiProxy
    fun toDTO(apiProxy: ServerConfigEntity.ApiProxy): ServerConfigDTO.ApiProxy
    fun fromDTO(wireServer: ServerConfigDTO): ServerConfig
    fun fromDTO(links: ServerConfigDTO.Links): ServerConfig.Links
    fun fromDTO(apiProxy: ServerConfigDTO.ApiProxy): ServerConfig.ApiProxy
    fun fromDTO(metadata: ServerConfigDTO.MetaData): ServerConfig.MetaData
    fun toEntity(serverLinks: ServerConfig): ServerConfigEntity
    fun toEntity(serverLinks: ServerConfig.Links): ServerConfigEntity.Links
    fun toEntity(apiProxy: ServerConfig.ApiProxy): ServerConfigEntity.ApiProxy
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
    fun fromEntity(serverConfigEntityLinks: ServerConfigEntity.Links): ServerConfig.Links
    fun fromEntity(apiProxy: ServerConfigEntity.ApiProxy): ServerConfig.ApiProxy
    fun fromEntity(serverConfigEntity: ServerConfigWithUserIdEntity): ServerConfigWithUserId
}

class ServerConfigMapperImpl(
    private val apiVersionMapper: ApiVersionMapper
) : ServerConfigMapper {
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO = with(serverConfig) {
        ServerConfigDTO(
            id = id,
            links = ServerConfigDTO.Links(
                api = links.api,
                accounts = links.accounts,
                webSocket = links.webSocket,
                blackList = links.blackList,
                teams = links.teams,
                website = links.website,
                title = links.title,
                isOnPremises = links.isOnPremises,
                apiProxy = links.apiProxy?.let { toDTO(it) },
                supportEmail = links.supportEmail,
            ),
            metaData = ServerConfigDTO.MetaData(
                federation = metaData.federation,
                commonApiVersion = apiVersionMapper.toDTO(metaData.commonApiVersion),
                domain = metaData.domain
            )
        )
    }

    override fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links = with(links) {
        ServerConfigDTO.Links(
            links.api,
            links.accounts,
            links.webSocket,
            links.blackList,
            links.teams,
            links.website,
            title,
            isOnPremises,
            links.apiProxy?.let { toDTO(it) },
            links.supportEmail,
        )
    }

    override fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO = with(serverConfigEntity) {
        ServerConfigDTO(
            id = id,
            links = ServerConfigDTO.Links(
                api = links.api,
                accounts = links.accounts,
                webSocket = links.webSocket,
                blackList = links.blackList,
                teams = links.teams,
                website = links.website,
                title = links.title,
                isOnPremises = links.isOnPremises,
                apiProxy = links.apiProxy?.let { toDTO(it) }
            ),
            metaData = ServerConfigDTO.MetaData(
                federation = metaData.federation,
                commonApiVersion = apiVersionMapper.toDTO(metaData.apiVersion),
                domain = metaData.domain
            )
        )
    }

    override fun toDTO(apiProxy: ServerConfig.ApiProxy): ServerConfigDTO.ApiProxy =
        with(apiProxy) { ServerConfigDTO.ApiProxy(needsAuthentication, host, port) }

    override fun toDTO(apiProxy: ServerConfigEntity.ApiProxy): ServerConfigDTO.ApiProxy =
        with(apiProxy) { ServerConfigDTO.ApiProxy(needsAuthentication, host, port) }

    override fun fromDTO(wireServer: ServerConfigDTO): ServerConfig = with(wireServer) {
        ServerConfig(id = id, links = fromDTO(links), metaData = fromDTO(metaData))
    }

    override fun fromDTO(links: ServerConfigDTO.Links): ServerConfig.Links = with(links) {
        ServerConfig.Links(
            api = api,
            website = website,
            webSocket = webSocket,
            accounts = accounts,
            blackList = blackList,
            teams = teams,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { fromDTO(it) },
            supportEmail = supportEmail,
        )
    }

    override fun fromDTO(apiProxy: ServerConfigDTO.ApiProxy): ServerConfig.ApiProxy = with(apiProxy) {
        ServerConfig.ApiProxy(needsAuthentication = needsAuthentication, host = host, port = port)
    }

    override fun fromDTO(metadata: ServerConfigDTO.MetaData): ServerConfig.MetaData = with(metadata) {
        ServerConfig.MetaData(federation, apiVersionMapper.fromDTO(commonApiVersion), domain)
    }

    override fun toEntity(serverLinks: ServerConfig): ServerConfigEntity = with(serverLinks) {
        ServerConfigEntity(
            id = id,
            links = toEntity(links),
            metaData = ServerConfigEntity.MetaData(
                federation = metaData.federation,
                apiVersion = metaData.commonApiVersion.version,
                domain = metaData.domain
            )
        )
    }

    override fun toEntity(serverLinks: ServerConfig.Links): ServerConfigEntity.Links = with(serverLinks) {
        ServerConfigEntity.Links(
            api = api,
            accounts = accounts,
            webSocket = webSocket,
            blackList = blackList,
            teams = teams,
            website = website,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { toEntity(it) }
        )
    }

    override fun toEntity(apiProxy: ServerConfig.ApiProxy): ServerConfigEntity.ApiProxy = with(apiProxy) {
        ServerConfigEntity.ApiProxy(
            needsAuthentication = needsAuthentication,
            host = host,
            port = port
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig = with(serverConfigEntity) {
        ServerConfig(
            id = id,
            links = fromEntity(links),
            metaData = ServerConfig.MetaData(metaData.federation, metaData.apiVersion.toCommonApiVersionType(), metaData.domain)
        )
    }

    override fun fromEntity(serverConfigEntityLinks: ServerConfigEntity.Links): ServerConfig.Links = with(serverConfigEntityLinks) {
        ServerConfig.Links(
            api = api,
            accounts = accounts,
            webSocket = webSocket,
            blackList = blackList,
            teams = teams,
            website = website,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { fromEntity(it) }
        )
    }

    override fun fromEntity(apiProxy: ServerConfigEntity.ApiProxy): ServerConfig.ApiProxy = with(apiProxy) {
        ServerConfig.ApiProxy(
            needsAuthentication = needsAuthentication,
            host = host,
            port = port
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigWithUserIdEntity): ServerConfigWithUserId =
        ServerConfigWithUserId(
            fromEntity(serverConfigEntity.serverConfig),
            UserId(serverConfigEntity.userId.value, serverConfigEntity.userId.domain)
        )
}

interface ApiVersionMapper {
    fun fromDTO(apiVersionDTO: ApiVersionDTO): CommonApiVersionType
    fun toDTO(commonApiVersion: CommonApiVersionType): ApiVersionDTO
    fun toDTO(commonApiVersion: Int): ApiVersionDTO

}

class ApiVersionMapperImpl : ApiVersionMapper {
    override fun fromDTO(apiVersionDTO: ApiVersionDTO): CommonApiVersionType = when (apiVersionDTO) {
        ApiVersionDTO.Invalid.New -> CommonApiVersionType.New
        ApiVersionDTO.Invalid.Unknown -> CommonApiVersionType.Unknown
        is ApiVersionDTO.Valid -> CommonApiVersionType.Valid(apiVersionDTO.version)
    }

    override fun toDTO(commonApiVersion: CommonApiVersionType): ApiVersionDTO = when (commonApiVersion) {
        CommonApiVersionType.New -> ApiVersionDTO.Invalid.New
        CommonApiVersionType.Unknown -> ApiVersionDTO.Invalid.Unknown
        is CommonApiVersionType.Valid -> ApiVersionDTO.Valid(commonApiVersion.version)
    }

    override fun toDTO(commonApiVersion: Int): ApiVersionDTO = ApiVersionDTO.fromInt(commonApiVersion)
}
