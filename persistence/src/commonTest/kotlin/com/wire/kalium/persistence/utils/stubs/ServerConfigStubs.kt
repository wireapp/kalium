package com.wire.kalium.persistence.utils.stubs

import com.wire.kalium.persistence.model.ServerConfigEntity

internal fun newServerConfig(id: Int) = ServerConfigEntity(
    id = "config-$id",
    ServerConfigEntity.Links(
        api = "https://server$id-apiBaseUrl.de",
        accounts = "https://server$id-accountBaseUrl.de",
        webSocket = "https://server$id-webSocketBaseUrl.de",
        blackList = "https://server$id-blackListUrl.de",
        teams = "https://server$id-teamsUrl.de",
        website = "https://server$id-websiteUrl.de",
        title = "server$id-title"
    ),
    ServerConfigEntity.MetaData(
        federation = false,
        domain = "wire-$id.com",
        apiVersion = 1
    )
)
