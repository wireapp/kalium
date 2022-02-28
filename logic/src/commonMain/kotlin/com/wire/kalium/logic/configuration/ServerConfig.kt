package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.BackendConfig
import com.wire.kalium.persistence.model.NetworkConfig
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val apiBaseUrl: String,
    val accountsBaseUrl: String,
    val webSocketBaseUrl: String,
    val blackListUrl: String,
    val teamsUrl: String,
    val websiteUrl: String,
    val title: String
) {
    companion object {
        val PRODUCTION = ServerConfig(
            apiBaseUrl = """prod-nginz-https.wire.com""",
            accountsBaseUrl = """account.wire.com""",
            webSocketBaseUrl = """prod-nginz-ssl.wire.com""",
            teamsUrl = """teams.wire.com""",
            blackListUrl = """clientblacklist.wire.com/prod""",
            websiteUrl = """wire.com""",
            title = "Production"
        )
        val STAGING = ServerConfig(
            apiBaseUrl = """staging-nginz-https.zinfra.io""",
            accountsBaseUrl = """wire-account-staging.zinfra.io""",
            webSocketBaseUrl = """staging-nginz-ssl.zinfra.io""",
            teamsUrl = """wire-teams-staging.zinfra.io""",
            blackListUrl = """clientblacklist.wire.com/staging""",
            websiteUrl = """wire.com""",
            title = "Staging"
        )
        val DEFAULT = STAGING
    }
}

interface ServerConfigMapper {
    fun toBackendConfig(serverConfig: ServerConfig): BackendConfig
    fun fromBackendConfig(backendConfig: BackendConfig): ServerConfig
    fun toNetworkConfig(serverConfig: ServerConfig): NetworkConfig
    fun fromNetworkConfig(networkConfig: NetworkConfig): ServerConfig
}

class ServerConfigMapperImpl : ServerConfigMapper {
    // TODO: url validation check e.g. remove https:// since ktor will control the http protocol
    override fun toBackendConfig(serverConfig: ServerConfig): BackendConfig =
        with(serverConfig) { BackendConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

    override fun fromBackendConfig(backendConfig: BackendConfig): ServerConfig =
        with(backendConfig) { ServerConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, title) }

    override fun toNetworkConfig(serverConfig: ServerConfig): NetworkConfig =
        with(serverConfig) {
            NetworkConfig(apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, serverConfig.title)
        }

    override fun fromNetworkConfig(networkConfig: NetworkConfig): ServerConfig =
        with(networkConfig) {
            ServerConfig(
                apiBaseUrl,
                accountBaseUrl,
                webSocketBaseUrl,
                blackListUrl,
                teamsUrl,
                websiteUrl,
                networkConfig.title
            )
        }

}
