package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.BackendConfig

data class ServerConfig(
    val apiBaseUrl: String,
    val accountsUrl: String,
    val webSocketUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String
) {
    companion object {
        val PRODUCTION = ServerConfig(
            apiBaseUrl = """"prod-nginz-https.wire.com"""",
            accountsUrl = """"account.wire.com"""",
            webSocketUrl = """"prod-nginz-ssl.wire.com"""",
            teamsUrl = """"teams.wire.com"""",
            blackListUrl = """clientblacklist.wire.com/prod""",
            websiteUrl = """wire.com"""
        )
        val STAGING = ServerConfig(
            apiBaseUrl = """"staging-nginz-https.zinfra.io"""",
            accountsUrl = """"wire-account-staging.zinfra.io"""",
            webSocketUrl = """"staging-nginz-ssl.zinfra.io""",
            teamsUrl = """"wire-teams-staging.zinfra.io"""",
            blackListUrl = """clientblacklist.wire.com/staging""",
            websiteUrl = """wire.com"""

        )
    }
}

class ServerConfigMapper {
    // TODO: url validation check e.g. remove https:// since ktor will control the http protocol
    fun toBackendConfig(serverConfig: ServerConfig): BackendConfig =
        with(serverConfig) { BackendConfig(apiBaseUrl = apiBaseUrl, accountsUrl = accountsUrl, webSocketUrl = webSocketUrl) }
}

/*
sealed class BuildType {
    object Release : BuildType()
    object Debug : BuildType()
    data class Custom(val networkConfig: NetworkConfig) : BuildType()
}

data class NetworkConfig(
    val apiBaseUrl: String,
    val accountsUrl: String,
    val webSocketUrl: String
)

 */
