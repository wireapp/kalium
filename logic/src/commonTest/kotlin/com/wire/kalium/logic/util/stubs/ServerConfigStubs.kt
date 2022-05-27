package com.wire.kalium.logic.util.stubs

import com.wire.kalium.logic.configuration.server.CommonApiVersionType
import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.network.api.configuration.EndPoints
import com.wire.kalium.network.api.configuration.ServerConfigResponse
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url

internal fun newTestServer(id: Int) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title"
        ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfig(id: Int) = ServerConfig(
    id = "config-$id",
    links = ServerConfig.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title"
        ),
    metaData = ServerConfig.MetaData(
        commonApiVersion = CommonApiVersionType.Valid(id),
        domain = "domain$id.com",
        federation = false
    )
)

internal fun newServerConfigResponse(id: Int) = ServerConfigResponse(
    EndPoints(
        apiBaseUrl = "https://server$id-apiBaseUrl.de",
        accountsBaseUrl = "https://server$id-accountBaseUrl.de",
        webSocketBaseUrl = "https://server$id-webSocketBaseUrl.de",
        blackListUrl = "https://server$id-blackListUrl.de",
        teamsUrl = "https://server$id-teamsUrl.de",
        websiteUrl = "https://server$id-websiteUrl.de"
    ),
    title = "server$id-title",
)

internal fun newServerConfigEntity(id: Int) = ServerConfigEntity(
    id = "config-$id",
    apiBaseUrl = "https://server$id-apiBaseUrl.de",
    accountBaseUrl = "https://server$id-accountBaseUrl.de",
    webSocketBaseUrl = "https://server$id-webSocketBaseUrl.de",
    blackListUrl = "https://server$id-blackListUrl.de",
    teamsUrl = "https://server$id-teamsUrl.de",
    websiteUrl = "https://server$id-websiteUrl.de",
    title = "server$id-title",
    commonApiVersion = id,
    domain = "domain$id.com",
    federation = false
)

internal fun newServerConfigDTO(id: Int) = ServerConfigDTO(
    id = "config-$id",
    links = ServerConfigDTO.Links(
        api = Url("https://server$id-apiBaseUrl.de"),
        accounts = Url("https://server$id-accountBaseUrl.de"),
        webSocket = Url("https://server$id-webSocketBaseUrl.de"),
        blackList = Url("https://server$id-blackListUrl.de"),
        teams = Url("https://server$id-teamsUrl.de"),
        website = Url("https://server$id-websiteUrl.de"),
        title = "server$id-title"
    ),
    ServerConfigDTO.MetaData(
        false,
        ApiVersionDTO.fromInt(id),
        domain = "domain$id.com",
    )
)


