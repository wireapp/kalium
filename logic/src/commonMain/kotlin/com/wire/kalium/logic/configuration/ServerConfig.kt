package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.persistence.network_config.NetworkConfig

data class ServerConfig(
    val apiBaseUrl: String,
    val accountsBaseUrl: String,
    val webSocketBaseUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String
) {
    companion object {
        val PRODUCTION = ServerConfig(
            apiBaseUrl = """prod-nginz-https.wire.com""",
            accountsBaseUrl = """account.wire.com""",
            webSocketBaseUrl = """prod-nginz-ssl.wire.com""",
            teamsUrl = """teams.wire.com""",
            blackListUrl = """clientblacklist.wire.com/prod""",
            websiteUrl = """wire.com"""
        )
        val STAGING = ServerConfig(
            apiBaseUrl = """staging-nginz-https.zinfra.io""",
            accountsBaseUrl = """wire-account-staging.zinfra.io""",
            webSocketBaseUrl = """staging-nginz-ssl.zinfra.io""",
            teamsUrl = """wire-teams-staging.zinfra.io""",
            blackListUrl = """clientblacklist.wire.com/staging""",
            websiteUrl = """wire.com"""
        )
    }
}

class ServerConfigMapper {
    // TODO: url validation check e.g. remove https:// since ktor will control the http protocol
    fun toBackendConfig(serverConfig: ServerConfig): BackendConfig =
        with(serverConfig) { BackendConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

    fun fromBackendConfig(backendConfig: BackendConfig): ServerConfig =
        with(backendConfig) { ServerConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

    fun toNetworkConfig(serverConfig: ServerConfig): NetworkConfig =
        with(serverConfig) { NetworkConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }

    fun fromNetworkConfig(networkConfig: NetworkConfig): ServerConfig =
        with(networkConfig) { ServerConfig(apiBaseUrl, accountBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl) }
}
