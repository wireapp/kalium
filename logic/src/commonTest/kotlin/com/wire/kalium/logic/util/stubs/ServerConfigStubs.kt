package com.wire.kalium.logic.util.stubs

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url

internal fun newServerConfig(id: Int) = ServerConfig(
    id = "config-$id",
    apiBaseUrl = "https://server$id-apiBaseUrl.de",
    accountsBaseUrl = "https://server$id-accountBaseUrl.de",
    webSocketBaseUrl = "https://server$id-webSocketBaseUrl.de",
    blackListUrl = "https://server$id-blackListUrl.de",
    teamsUrl = "https://server$id-teamsUrl.de",
    websiteUrl = "https://server$id-websiteUrl.de",
    title = "server$id-title",
    commonApiVersion = id,
    domain = "domain$id.com",
    federation = false
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
    apiBaseUrl = Url("https://server$id-apiBaseUrl.de"),
    accountsBaseUrl = Url("https://server$id-accountBaseUrl.de"),
    webSocketBaseUrl = Url("https://server$id-webSocketBaseUrl.de"),
    blackListUrl = Url("https://server$id-blackListUrl.de"),
    teamsUrl = Url("https://server$id-teamsUrl.de"),
    websiteUrl = Url("https://server$id-websiteUrl.de"),
    title = "server$id-title"
)


