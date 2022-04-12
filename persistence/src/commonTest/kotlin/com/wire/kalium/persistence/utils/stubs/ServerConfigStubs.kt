package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.model.ServerConfigEntity

internal fun newServerConfig(id: Int) = ServerConfigEntity(
    id = "config-$id",
    apiBaseUrl = "https://server$id-apiBaseUrl.de",
    accountBaseUrl = "https://server$id-accountBaseUrl.de",
    webSocketBaseUrl = "https://server$id-webSocketBaseUrl.de",
    blackListUrl = "https://server$id-blackListUrl.de",
    teamsUrl = "https://server$id-teamsUrl.de",
    websiteUrl = "https://server$id-websiteUrl.de",
    title = "server$id-title",
)
