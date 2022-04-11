package com.wire.kalium.logic.configuration

import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
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
            apiBaseUrl = """https://prod-nginz-https.wire.com""",
            accountsBaseUrl = """https://account.wire.com""",
            webSocketBaseUrl = """https://prod-nginz-ssl.wire.com""",
            teamsUrl = """https://teams.wire.com""",
            blackListUrl = """https://clientblacklist.wire.com/prod""",
            websiteUrl = """https://wire.com""",
            title = "Production"
        )
        val STAGING = ServerConfig(
            apiBaseUrl = """https://staging-nginz-https.zinfra.io""",
            accountsBaseUrl = """https://wire-account-staging.zinfra.io""",
            webSocketBaseUrl = """https://staging-nginz-ssl.zinfra.io""",
            teamsUrl = """https://wire-teams-staging.zinfra.io""",
            blackListUrl = """https://clientblacklist.wire.com/staging""",
            websiteUrl = """https://wire.com""",
            title = "Staging"
        )
        val DEFAULT = STAGING
    }
}

interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun fromDTO(serverConfigDTO: ServerConfigDTO): ServerConfig
    fun toEntity(serverConfig: ServerConfig): ServerConfigEntity
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
}

class ServerConfigMapperImpl : ServerConfigMapper {
    // TODO: url validation check e.g. remove https:// since ktor will control the http protocol
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO =
        with(serverConfig) { ServerConfigDTO(Url(apiBaseUrl), Url(accountsBaseUrl), Url(webSocketBaseUrl), Url(blackListUrl), Url(teamsUrl), Url(websiteUrl), title) }

    override fun fromDTO(serverConfigDTO: ServerConfigDTO): ServerConfig =
        with(serverConfigDTO) { ServerConfig(apiBaseUrl.toString(), accountsBaseUrl.toString(), webSocketBaseUrl.toString(), blackListUrl.toString(), teamsUrl.toString(), websiteUrl.toString(), title) }

    override fun toEntity(serverConfig: ServerConfig): ServerConfigEntity =
        with(serverConfig) {
            ServerConfigEntity(null, apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, serverConfig.title)
        }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig =
        with(serverConfigEntity) {
            ServerConfig(
                apiBaseUrl,
                accountBaseUrl,
                webSocketBaseUrl,
                blackListUrl,
                teamsUrl,
                websiteUrl,
                serverConfigEntity.title
            )
        }

}
