/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

@file:Suppress("TooManyFunctions")

package com.wire.kalium.logic.configuration.server

import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
import com.wire.kalium.persistence.model.ServerConfigWithUserIdEntity
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

data class ServerConfigWithUserId(
    val serverConfig: ServerConfig,
    val userId: UserId
)

@Serializable
data class ServerConfig(
    @SerialName("config_id") val id: String,
    @SerialName("links") val links: Links,
    @SerialName("metadata") val metaData: MetaData
) {
    @Serializable
    data class Links(
        @SerialName("apiBaseUrl") val api: String,
        @SerialName("accountsBaseUrl") val accounts: String,
        @SerialName("webSocketBaseUrl") val webSocket: String,
        @SerialName("blackListUrl") val blackList: String,
        @SerialName("teamsUrl") val teams: String,
        @SerialName("websiteUrl") val website: String,
        @SerialName("title") val title: String,
        @SerialName("is_on_premises") val isOnPremises: Boolean,
        @SerialName("apiProxy") val apiProxy: ApiProxy?
    ) {
        val forgotPassword: String
            get() = URLBuilder().apply {
                val url = Url(accounts)
                host = url.host
                protocol = url.protocol
                pathSegments = url.pathSegments + FORGOT_PASSWORD_PATH
            }.buildString()

        val pricing: String
            get() = URLBuilder().apply {
                val url = Url(website)
                host = url.host
                protocol = url.protocol
                pathSegments = url.pathSegments + PRICING_PATH
            }.buildString()

        val tos: String
            get() = URLBuilder().apply {
                val url = Url(website)
                host = url.host
                protocol = url.protocol
                pathSegments = url.pathSegments + TOS_PATH
            }.buildString()
    }

    @Serializable
    data class MetaData(
        @SerialName("federation") val federation: Boolean,
        @SerialName("commonApiVersion")
        @Serializable(CommonApiVersionTypeSerializer::class)
        val commonApiVersion: CommonApiVersionType,
        @SerialName("domain") val domain: String?
    )

    @Serializable
    data class VersionInfo(
        @SerialName("federation") val federation: Boolean,
        @SerialName("supported") val supported: List<Int>,
        @SerialName("domain") val domain: String? = null,
        @SerialName("development") val developmentSupported: List<Int>? = null,
    )

    @Serializable
    data class ApiProxy(
        @SerialName("needsAuthentication") val needsAuthentication: Boolean,
        @SerialName("host") val host: String,
        @SerialName("port") val port: Int
    )

    companion object {
        val PRODUCTION = Links(
            api = """https://prod-nginz-https.wire.com""",
            accounts = """https://account.wire.com""",
            webSocket = """https://prod-nginz-ssl.wire.com""",
            teams = """https://teams.wire.com""",
            blackList = """https://clientblacklist.wire.com/prod""",
            website = """https://wire.com""",
            title = "production",
            isOnPremises = false,
            apiProxy = null
        )

        val STAGING = Links(
            api = """https://staging-nginz-https.zinfra.io""",
            accounts = """https://wire-account-staging.zinfra.io""",
            webSocket = """https://staging-nginz-ssl.zinfra.io""",
            teams = """https://wire-teams-staging.zinfra.io""",
            blackList = """https://clientblacklist.wire.com/staging""",
            website = """https://wire.com""",
            title = "staging",
            isOnPremises = false,
            apiProxy = null
        )
        val DEFAULT = PRODUCTION

        private const val FORGOT_PASSWORD_PATH = "forgot"
        private const val PRICING_PATH = "pricing"
        private const val TOS_PATH = "legal"
    }
}

interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links
    fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO
    fun toDTO(apiProxy: ServerConfig.ApiProxy): ServerConfigDTO.ApiProxy
    fun toDTO(apiProxy: ServerConfigEntity.ApiProxy): ServerConfigDTO.ApiProxy
    fun fromDTO(wireServer: ServerConfigDTO): ServerConfig
    fun fromDTO(links: ServerConfigDTO.Links): ServerConfig.Links
    fun fromDTO(apiProxy: ServerConfigDTO.ApiProxy): ServerConfig.ApiProxy
    fun fromDTO(metadata: ServerConfigDTO.MetaData): ServerConfig.MetaData
    fun toEntity(serverLinks: ServerConfig): ServerConfigEntity
    fun toEntity(serverLinks: ServerConfig.Links): ServerConfigEntity.Links
    fun toEntity(apiProxy: ServerConfig.ApiProxy): ServerConfigEntity.ApiProxy
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
    fun fromEntity(serverConfigEntityLinks: ServerConfigEntity.Links): ServerConfig.Links
    fun fromEntity(apiProxy: ServerConfigEntity.ApiProxy): ServerConfig.ApiProxy
    fun fromEntity(serverConfigEntity: ServerConfigWithUserIdEntity): ServerConfigWithUserId
}

class ServerConfigMapperImpl(
    private val apiVersionMapper: ApiVersionMapper
) : ServerConfigMapper {
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO = with(serverConfig) {
        ServerConfigDTO(
            id = id,
            links = ServerConfigDTO.Links(
                links.api,
                links.accounts,
                links.webSocket,
                links.blackList,
                links.teams,
                links.website,
                links.title,
                isOnPremises = links.isOnPremises,
                apiProxy = links.apiProxy?.let { toDTO(it) }
            ),
            metaData = ServerConfigDTO.MetaData(
                federation = metaData.federation,
                commonApiVersion = apiVersionMapper.toDTO(metaData.commonApiVersion),
                domain = metaData.domain
            )
        )
    }

    override fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links = with(links) {
        ServerConfigDTO.Links(
            links.api,
            links.accounts,
            links.webSocket,
            links.blackList,
            links.teams,
            links.website,
            title,
            isOnPremises,
            links.apiProxy?.let { toDTO(it) }
        )
    }

    override fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO = with(serverConfigEntity) {
        ServerConfigDTO(
            id = id,
            links = ServerConfigDTO.Links(
                api = links.api,
                accounts = links.accounts,
                webSocket = links.webSocket,
                blackList = links.blackList,
                teams = links.teams,
                website = links.website,
                title = links.title,
                isOnPremises = links.isOnPremises,
                apiProxy = links.apiProxy?.let { toDTO(it) }
            ),
            metaData = ServerConfigDTO.MetaData(
                federation = metaData.federation, commonApiVersion = apiVersionMapper.toDTO(metaData.apiVersion), domain = metaData.domain
            )
        )
    }

    override fun toDTO(apiProxy: ServerConfig.ApiProxy): ServerConfigDTO.ApiProxy =
        with(apiProxy) { ServerConfigDTO.ApiProxy(needsAuthentication, host, port) }

    override fun toDTO(apiProxy: ServerConfigEntity.ApiProxy): ServerConfigDTO.ApiProxy =
        with(apiProxy) { ServerConfigDTO.ApiProxy(needsAuthentication, host, port) }

    override fun fromDTO(wireServer: ServerConfigDTO): ServerConfig = with(wireServer) {
        ServerConfig(id = id, links = fromDTO(links), metaData = fromDTO(metaData))
    }

    override fun fromDTO(links: ServerConfigDTO.Links): ServerConfig.Links = with(links) {
        ServerConfig.Links(
            api = api,
            website = website,
            webSocket = webSocket,
            accounts = accounts,
            blackList = blackList,
            teams = teams,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { fromDTO(it) }
        )
    }

    override fun fromDTO(apiProxy: ServerConfigDTO.ApiProxy): ServerConfig.ApiProxy = with(apiProxy) {
        ServerConfig.ApiProxy(needsAuthentication = needsAuthentication, host = host, port = port)
    }

    override fun fromDTO(metadata: ServerConfigDTO.MetaData): ServerConfig.MetaData = with(metadata) {
        ServerConfig.MetaData(federation, apiVersionMapper.fromDTO(commonApiVersion), domain)
    }

    override fun toEntity(serverLinks: ServerConfig): ServerConfigEntity = with(serverLinks) {
        ServerConfigEntity(
            id = id,
            links = toEntity(links),
            metaData = ServerConfigEntity.MetaData(
                federation = metaData.federation, apiVersion = metaData.commonApiVersion.version, domain = metaData.domain
            )
        )
    }

    override fun toEntity(serverLinks: ServerConfig.Links): ServerConfigEntity.Links = with(serverLinks) {
        ServerConfigEntity.Links(
            api = api,
            accounts = accounts,
            webSocket = webSocket,
            blackList = blackList,
            teams = teams,
            website = website,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { toEntity(it) }
        )
    }

    override fun toEntity(apiProxy: ServerConfig.ApiProxy): ServerConfigEntity.ApiProxy = with(apiProxy) {
        ServerConfigEntity.ApiProxy(
            needsAuthentication = needsAuthentication,
            host = host,
            port = port
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig = with(serverConfigEntity) {
        ServerConfig(
            id = id,
            links = fromEntity(links),
            metaData = ServerConfig.MetaData(metaData.federation, metaData.apiVersion.toCommonApiVersionType(), metaData.domain)
        )
    }

    override fun fromEntity(serverConfigEntityLinks: ServerConfigEntity.Links): ServerConfig.Links = with(serverConfigEntityLinks) {
        ServerConfig.Links(
            api = api,
            accounts = accounts,
            webSocket = webSocket,
            blackList = blackList,
            teams = teams,
            website = website,
            title = title,
            isOnPremises = isOnPremises,
            apiProxy = apiProxy?.let { fromEntity(it) }
        )
    }

    override fun fromEntity(apiProxy: ServerConfigEntity.ApiProxy): ServerConfig.ApiProxy = with(apiProxy) {
        ServerConfig.ApiProxy(
            needsAuthentication = needsAuthentication,
            host = host,
            port = port
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigWithUserIdEntity): ServerConfigWithUserId =
        ServerConfigWithUserId(
            fromEntity(serverConfigEntity.serverConfig),
            serverConfigEntity.userId.toModel()
        )
}

sealed interface CommonApiVersionType {
    val version: Int

    data object New : CommonApiVersionType {
        override val version: Int
            get() = NEW_API_VERSION_NUMBER
    }

    data object Unknown : CommonApiVersionType {
        override val version: Int
            get() = UNKNOWN_API_VERSION_NUMBER
    }

    data class Valid(override val version: Int) : CommonApiVersionType

    companion object {
        const val NEW_API_VERSION_NUMBER = -1
        const val UNKNOWN_API_VERSION_NUMBER = -2
        const val MINIMUM_VALID_API_VERSION = 0
    }
}

fun Int?.toCommonApiVersionType() = when {
    this != null && this >= CommonApiVersionType.MINIMUM_VALID_API_VERSION -> CommonApiVersionType.Valid(this)
    this == CommonApiVersionType.NEW_API_VERSION_NUMBER -> CommonApiVersionType.New
    else -> CommonApiVersionType.Unknown
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(CommonApiVersionType::class)
class CommonApiVersionTypeSerializer : KSerializer<CommonApiVersionType> {
    override val descriptor = PrimitiveSerialDescriptor("common_api_version", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: CommonApiVersionType) {
        encoder.encodeInt(value.version)
    }

    override fun deserialize(decoder: Decoder): CommonApiVersionType = decoder.decodeInt().toCommonApiVersionType()
}

interface ApiVersionMapper {
    fun fromDTO(apiVersionDTO: ApiVersionDTO): CommonApiVersionType
    fun toDTO(commonApiVersion: CommonApiVersionType): ApiVersionDTO
    fun toDTO(commonApiVersion: Int): ApiVersionDTO

}

class ApiVersionMapperImpl : ApiVersionMapper {
    override fun fromDTO(apiVersionDTO: ApiVersionDTO): CommonApiVersionType = when (apiVersionDTO) {
        ApiVersionDTO.Invalid.New -> CommonApiVersionType.New
        ApiVersionDTO.Invalid.Unknown -> CommonApiVersionType.Unknown
        is ApiVersionDTO.Valid -> CommonApiVersionType.Valid(apiVersionDTO.version)
    }

    override fun toDTO(commonApiVersion: CommonApiVersionType): ApiVersionDTO = when (commonApiVersion) {
        CommonApiVersionType.New -> ApiVersionDTO.Invalid.New
        CommonApiVersionType.Unknown -> ApiVersionDTO.Invalid.Unknown
        is CommonApiVersionType.Valid -> ApiVersionDTO.Valid(commonApiVersion.version)
    }

    override fun toDTO(commonApiVersion: Int): ApiVersionDTO = ApiVersionDTO.fromInt(commonApiVersion)
}
