package com.wire.kalium.logic.configuration.server

import com.benasher44.uuid.uuid4
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

data class WireServer(
    val id: String,
    val title: String,
    val links: Links,
    val metaData: MetaData
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
        val PRODUCTION = Links(
            api = """https://prod-nginz-https.wire.com""",
            accounts = """https://account.wire.com""",
            webSocket = """https://prod-nginz-ssl.wire.com""",
            teams = """https://teams.wire.com""",
            blackList = """https://clientblacklist.wire.com/prod""",
            website = """https://wire.com""",
        )
        val STAGING = Links(
            api = """https://staging-nginz-https.zinfra.io""",
            accounts = """https://wire-account-staging.zinfra.io""",
            webSocket = """https://staging-nginz-ssl.zinfra.io""",
            teams = """https://wire-teams-staging.zinfra.io""",
            blackList = """https://clientblacklist.wire.com/staging""",
            website = """https://wire.com"""
        )
        val DEFAULT = PRODUCTION
    }
}


@Deprecated("old model", replaceWith = ReplaceWith("com.wire.kalium.logic.configuration.server.WireServer"))
@Serializable
data class ServerConfig(
    @SerialName("id") val id: String,
    @SerialName("apiBaseUrl") val apiBaseUrl: String,
    @SerialName("accountsBaseUrl") val accountsBaseUrl: String,
    @SerialName("webSocketBaseUrl") val webSocketBaseUrl: String,
    @SerialName("blackListUrl") val blackListUrl: String,
    @SerialName("teamsUrl") val teamsUrl: String,
    @SerialName("websiteUrl") val websiteUrl: String,
    @SerialName("title") val title: String,
    @SerialName("federation") val federation: Boolean,
    @SerialName("commonApiVersion") @Serializable(with = CommonApiVersionTypeSerializer::class) val commonApiVersion: CommonApiVersionType,
    @SerialName("domain") val domain: String?
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
            title = "Production",
            federation = false,
            commonApiVersion = CommonApiVersionType.Valid(1), // TODO: fetch the real value
            domain = "wire.com"
        )
        val STAGING = ServerConfig(
            id = uuid4().toString(),
            apiBaseUrl = """https://staging-nginz-https.zinfra.io""",
            accountsBaseUrl = """https://wire-account-staging.zinfra.io""",
            webSocketBaseUrl = """https://staging-nginz-ssl.zinfra.io""",
            teamsUrl = """https://wire-teams-staging.zinfra.io""",
            blackListUrl = """https://clientblacklist.wire.com/staging""",
            websiteUrl = """https://wire.com""",
            title = "Staging",
            federation = false,
            commonApiVersion = CommonApiVersionType.Valid(1), // TODO: fetch the real value
            domain = "staging.zinfra.io"
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
                title,
                commonApiVersion.version
            )
        }

    override fun toEntity(serverConfig: ServerConfig): ServerConfigEntity =
        with(serverConfig) {
            ServerConfigEntity(
                id,
                apiBaseUrl,
                accountsBaseUrl,
                webSocketBaseUrl,
                blackListUrl,
                teamsUrl,
                websiteUrl,
                title,
                federation,
                commonApiVersion.version,
                domain
            )
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
                title,
                federation,
                commonApiVersion.toCommonApiVersionType(),
                domain
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
