package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
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
    val id: String, val title: String, val links: Links, val metaData: MetaData
) {
    @Serializable
    data class Links(
        @SerialName("apiBaseUrl") val api: String,
        @SerialName("accountsBaseUrl") val accounts: String,
        @SerialName("webSocketBaseUrl") val webSocket: String,
        @SerialName("blackListUrl") val blackList: String,
        @SerialName("teamsUrl") val teams: String,
        @SerialName("websiteUrl") val website: String,
    )

    @Serializable
    data class MetaData(
        @SerialName("federation") val federation: Boolean,
        @SerialName("commonApiVersion") @Serializable(with = CommonApiVersionTypeSerializer::class) val commonApiVersion: CommonApiVersionType,
        @SerialName("domain") val domain: String?
    )

    companion object {
        val PRODUCTION = ServerConfig(
            id = uuid4().toString(),
            title = "production",
            links = Links(
                api = """https://prod-nginz-https.wire.com""",
                accounts = """https://account.wire.com""",
                webSocket = """https://prod-nginz-ssl.wire.com""",
                teams = """https://teams.wire.com""",
                blackList = """https://clientblacklist.wire.com/prod""",
                website = """https://wire.com""",
            ),
            metaData = MetaData(
                federation = false,
                commonApiVersion = CommonApiVersionType.Valid(1), // TODO: fetch the real value
                domain = "wire.com"
            )
        )


        val STAGING = ServerConfig(
            id = uuid4().toString(),
            title = "staging",
            links = Links(
                api = """https://staging-nginz-https.zinfra.io""",
                accounts = """https://wire-account-staging.zinfra.io""",
                webSocket = """https://staging-nginz-ssl.zinfra.io""",
                teams = """https://wire-teams-staging.zinfra.io""",
                blackList = """https://clientblacklist.wire.com/staging""",
                website = """https://wire.com"""
            ),
            metaData = MetaData(
                federation = false,
                commonApiVersion = CommonApiVersionType.Valid(1), // TODO: fetch the real value
                domain = "staging.zinfra.io"
            )
        )
        val DEFAULT = PRODUCTION
    }
}


interface ServerConfigMapper {
    fun toDTO(serverConfig: ServerConfig): ServerConfigDTO
    fun toWireServerDTO(serverConfig: ServerConfig): ServerConfigDTO

    fun fromDTO(wireServer: ServerConfigDTO): ServerConfig
    fun toEntity(backend: ServerConfig): ServerConfigEntity
    fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig
}

class ServerConfigMapperImpl(
    private val apiVersionMapper: ApiVersionMapper
) : ServerConfigMapper {
    override fun toDTO(serverConfig: ServerConfig): ServerConfigDTO = with(serverConfig) {
        ServerConfigDTO(
            id = id,
            links = ServerConfigDTO.Links(
                Url(links.api),
                Url(links.accounts),
                Url(links.webSocket),
                Url(links.blackList),
                Url(links.teams),
                Url(links.website),
                title,
            ), ServerConfigDTO.MetaData(
                federation = metaData.federation,
                apiVersionMapper.toDTO(metaData.commonApiVersion),
                metaData.domain
            )
        )
    }

    override fun toWireServerDTO(serverConfig: ServerConfig): ServerConfigDTO = with(serverConfig) {
        ServerConfigDTO(
            id,
            ServerConfigDTO.Links(
                api = Url(links.api),
                accounts = Url(links.accounts),
                webSocket = Url(links.webSocket),
                blackList = Url(links.blackList),
                teams = Url(links.teams),
                website = Url(links.website),
                title = title
            ), ServerConfigDTO.MetaData(
                metaData.federation, apiVersionMapper.toDTO(metaData.commonApiVersion), metaData.domain
            )
        )
    }

    override fun fromDTO(wireServer: ServerConfigDTO): ServerConfig = with(wireServer) {
        ServerConfig(
            id = id,
            title = links.title,
            ServerConfig.Links(
                api = links.api.toString(),
                website = links.website.toString(),
                webSocket = links.webSocket.toString(),
                accounts = links.accounts.toString(),
                blackList = links.blackList.toString(),
                teams = links.teams.toString(),
            ),
            ServerConfig.MetaData(
                commonApiVersion = apiVersionMapper.fromDTO(metaData.commonApiVersion),
                federation = metaData.federation,
                domain = metaData.domain
            )
        )
    }

    override fun toEntity(backend: ServerConfig): ServerConfigEntity = with(backend) {
        ServerConfigEntity(
            id,
            links.api,
            links.accounts,
            links.webSocket,
            links.blackList,
            links.teams,
            links.website,
            title,
            metaData.federation,
            metaData.commonApiVersion.version,
            metaData.domain
        )
    }

    override fun fromEntity(serverConfigEntity: ServerConfigEntity): ServerConfig = with(serverConfigEntity) {
        ServerConfig(
            id,
            title,
            ServerConfig.Links(
                api = apiBaseUrl,
                accounts = accountBaseUrl,
                webSocket = webSocketBaseUrl,
                blackList = blackListUrl,
                teams = teamsUrl,
                website = websiteUrl
            ),
            ServerConfig.MetaData(federation, commonApiVersion.toCommonApiVersionType(), domain)
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
}

class ApiVersionMapperImpl: ApiVersionMapper {
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
}
