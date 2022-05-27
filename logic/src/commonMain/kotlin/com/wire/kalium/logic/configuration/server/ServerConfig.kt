package com.wire.kalium.logic.configuration.server

import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import com.wire.kalium.persistence.model.ServerConfigEntity
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

@Serializable
data class ServerConfig(
    @SerialName("config_id") val id: String,
    @SerialName("links") val links: Links,
    @SerialName("metadata")val metaData: MetaData
) {
    @Serializable
    data class Links(
        @SerialName("apiBaseUrl") val api: String,
        @SerialName("accountsBaseUrl") val accounts: String,
        @SerialName("webSocketBaseUrl") val webSocket: String,
        @SerialName("blackListUrl") val blackList: String,
        @SerialName("teamsUrl") val teams: String,
        @SerialName("websiteUrl") val website: String,
        @SerialName("title") val title: String
    )

    @Serializable
    data class MetaData(
        @SerialName("federation") val federation: Boolean,
        @SerialName("commonApiVersion")
        @Serializable(with = CommonApiVersionTypeSerializer::class)
        val commonApiVersion: CommonApiVersionType,
        @SerialName("domain") val domain: String?
    )

    companion object {
        val PRODUCTION = Links(
            api = """https://prod-nginz-https.wire.com""",
            accounts = """https://account.wire.com""",
            webSocket = """https://prod-nginz-ssl.wire.com""",
            teams = """https://teams.wire.com""",
            blackList = """https://clientblacklist.wire.com/prod""",
            website = """https://wire.com""",
            title = "production"
        )

        val STAGING = Links(
            api = """https://staging-nginz-https.zinfra.io""",
            accounts = """https://wire-account-staging.zinfra.io""",
            webSocket = """https://staging-nginz-ssl.zinfra.io""",
            teams = """https://wire-teams-staging.zinfra.io""",
            blackList = """https://clientblacklist.wire.com/staging""",
            website = """https://wire.com""",
            title = "staging"
        )
        val DEFAULT = PRODUCTION
    }
}


interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links
    fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO
    fun fromDTO(wireServer: ServerConfigDTO): ServerConfig
    fun toEntity(backend: ServerConfig): ServerConfigEntity
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
}

class ServerConfigMapperImpl(
    private val apiVersionMapper: ApiVersionMapper
) : ServerConfigMapper {
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO = with(serverConfig) {
        ServerConfigDTO(
            id = id, links = ServerConfigDTO.Links(
                Url(links.api),
                Url(links.accounts),
                Url(links.webSocket),
                Url(links.blackList),
                Url(links.teams),
                Url(links.website),
                links.title,
            ), ServerConfigDTO.MetaData(
                federation = metaData.federation, apiVersionMapper.toDTO(metaData.commonApiVersion), metaData.domain
            )
        )
    }

    override fun toDTO(links: ServerConfig.Links): ServerConfigDTO.Links = with(links) {
        ServerConfigDTO.Links(
            Url(links.api),
            Url(links.accounts),
            Url(links.webSocket),
            Url(links.blackList),
            Url(links.teams),
            Url(links.website),
            title,
        )
    }

    override fun toDTO(serverConfigEntity: ServerConfigEntity): ServerConfigDTO = with(serverConfigEntity) {
        ServerConfigDTO(
            id = id, links = ServerConfigDTO.Links(
                api = Url(apiBaseUrl),
                accounts = Url(accountBaseUrl),
                webSocket = Url(webSocketBaseUrl),
                blackList = Url(blackListUrl),
                teams = Url(teamsUrl),
                website = Url(websiteUrl),
                title = title,
            ), ServerConfigDTO.MetaData(
                federation = federation, commonApiVersion = apiVersionMapper.toDTO(commonApiVersion), domain
            )
        )
    }


    override fun fromDTO(wireServer: ServerConfigDTO): ServerConfig = with(wireServer) {
        ServerConfig(
            id = id, ServerConfig.Links(
                api = links.api.toString(),
                website = links.website.toString(),
                webSocket = links.webSocket.toString(),
                accounts = links.accounts.toString(),
                blackList = links.blackList.toString(),
                teams = links.teams.toString(),
                title = links.title,
            ), ServerConfig.MetaData(
                commonApiVersion = apiVersionMapper.fromDTO(metaData.commonApiVersion),
                federation = metaData.federation,
                domain = metaData.domain
            )
        )
    }

    override fun toEntity(backend: ServerConfig): ServerConfigEntity = with(backend) {
        ServerConfigEntity(
            id = id,
            apiBaseUrl = links.api,
            accountBaseUrl = links.accounts,
            webSocketBaseUrl = links.webSocket,
            blackListUrl = links.blackList,
            teamsUrl = links.teams,
            websiteUrl = links.website,
            title = links.title,
            federation = metaData.federation,
            commonApiVersion = metaData.commonApiVersion.version,
            domain = metaData.domain
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig = with(serverConfigEntity) {
        ServerConfig(
            id, ServerConfig.Links(
                api = apiBaseUrl,
                accounts = accountBaseUrl,
                webSocket = webSocketBaseUrl,
                blackList = blackListUrl,
                teams = teamsUrl,
                website = websiteUrl,
                title = title,
            ), ServerConfig.MetaData(federation, commonApiVersion.toCommonApiVersionType(), domain)
        )
    }
}

sealed class CommonApiVersionType(open val version: Int) {
    object New : CommonApiVersionType(NEW_API_VERSION_NUMBER)
    object Unknown : CommonApiVersionType(UNKNOWN_API_VERSION_NUMBER)
    data class Valid(override val version: Int) : CommonApiVersionType(version)

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
