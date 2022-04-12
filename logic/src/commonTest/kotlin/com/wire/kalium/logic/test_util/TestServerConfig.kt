package com.wire.kalium.logic.test_util

import com.wire.kalium.logic.configuration.ServerConfig

object TestServerConfig {

    val generic = ServerConfig(
        apiBaseUrl = "apiBaseUrl.com",
        accountsBaseUrl = "accountsUrl.com",
        webSocketBaseUrl = "webSocketUrl.com",
        blackListUrl = "blackListUrl.com",
        teamsUrl = "teamsUrl.com",
        websiteUrl = "websiteUrl.com",
        title = "Test Title"
    )
}
