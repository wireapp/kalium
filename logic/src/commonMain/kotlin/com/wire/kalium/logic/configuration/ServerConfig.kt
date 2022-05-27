package com.wire.kalium.logic.configuration

import com.benasher44.uuid.uuid4
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import io.ktor.http.Url
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    @SerialName("id") val id: String,
    @SerialName("apiBaseUrl") val apiBaseUrl: String,
    @SerialName("accountsBaseUrl") val accountsBaseUrl: String,
    @SerialName("webSocketBaseUrl") val webSocketBaseUrl: String,
    @SerialName("blackListUrl") val blackListUrl: String,
    @SerialName("teamsUrl") val teamsUrl: String,
    @SerialName("websiteUrl") val websiteUrl: String,
    @SerialName("title") val title: String
) {
    companion object {
        val PRODUCTION = ServerConfig(
            id = uuid4().toString(),
            apiBaseUrl = """https://prod-nginz-https.wire.com""",
            accountsBaseUrl = """https://account.wire.com""",
            webSocketBaseUrl = """https://prod-nginz-ssl.wire.com""",
            teamsUrl = """https://teams.wire.com""",
            blackListUrl = """https://clientblacklist.wire.com/prod""",
            websiteUrl = """https://wire.com""",
            title = "Production"
        )
        val STAGING = ServerConfig(
            id = uuid4().toString(),
            apiBaseUrl = """https://staging-nginz-https.zinfra.io""",
            accountsBaseUrl = """https://wire-account-staging.zinfra.io""",
            webSocketBaseUrl = """https://staging-nginz-ssl.zinfra.io""",
            teamsUrl = """https://wire-teams-staging.zinfra.io""",
            blackListUrl = """https://clientblacklist.wire.com/staging""",
            websiteUrl = """https://wire.com""",
            title = "Staging"
        )
        val DEFAULT = PRODUCTION
    }
}

interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun toEntity(serverConfig: ServerConfig): ServerConfigEntity
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
}

class ServerConfigMapperImpl : ServerConfigMapper {
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO =
        with(serverConfig) {
            ServerConfigDTO(
                Url(apiBaseUrl),
                Url(accountsBaseUrl),
                Url(webSocketBaseUrl),
                Url(blackListUrl),
                Url(teamsUrl),
                Url(websiteUrl),
                title
            )
        }

    override fun toEntity(serverConfig: ServerConfig): ServerConfigEntity =
        with(serverConfig) {
            ServerConfigEntity(id, apiBaseUrl, accountsBaseUrl, webSocketBaseUrl, blackListUrl, teamsUrl, websiteUrl, serverConfig.title)
        }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig =
        with(serverConfigEntity) {
            ServerConfig(
                id,
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
