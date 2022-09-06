package com.wire.kalium.logic.util.stubs

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity

internal fun newTestServer(id: Int) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false
    ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfig(id: Int, federationEnabled: Boolean = false) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false
    ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = federationEnabled
    )
)

internal fun newServerConfigEntity(id: Int) = ServerConfigEntity(
    id = "config-$id",
    links = ServerConfigEntity.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false
    ),
    metaData = ServerConfigEntity.MetaData(
        apiVersion = id,
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfigDTO(id: Int) = ServerConfigDTO(
    id = "config-$id",
    links = ServerConfigDTO.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title",
        false
    ),
    ServerConfigDTO.MetaData(
        false,
        ApiVersionDTO.fromInt(id),
        domain = "domain$id.com",
    )
)
