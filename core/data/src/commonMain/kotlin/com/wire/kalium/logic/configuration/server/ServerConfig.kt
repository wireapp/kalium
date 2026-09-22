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

import com.wire.kalium.logic.data.user.UserId
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

public data class ServerConfigWithUserId(
    val serverConfig: ServerConfig,
    val userId: UserId
)

@Serializable
public data class ServerConfig(
    @SerialName("config_id") val id: String,
    @SerialName("links") val links: Links,
    @SerialName("metadata") val metaData: MetaData
) {
    @Serializable
    public data class Links(
        @SerialName("apiBaseUrl") val api: String,
        @SerialName("accountsBaseUrl") val accounts: String,
        @SerialName("webSocketBaseUrl") val webSocket: String,
        @SerialName("blackListUrl") val blackList: String,
        @SerialName("teamsUrl") val teams: String,
        @SerialName("websiteUrl") val website: String,
        @SerialName("title") val title: String,
        @SerialName("is_on_premises") val isOnPremises: Boolean,
        @SerialName("apiProxy") val apiProxy: ApiProxy?,
        @SerialName("supportEmail") val supportEmail: String? = null,
    ) {
        val forgotPassword: String
            get() = URLBuilder().apply {
                val url = Url(accounts)
                host = url.host
                protocol = url.protocol
                pathSegments = url.rawSegments + FORGOT_PASSWORD_PATH
            }.buildString()

        val pricing: String
            get() = URLBuilder().apply {
                val url = Url(website)
                host = url.host
                protocol = url.protocol
                pathSegments = url.rawSegments + PRICING_PATH
            }.buildString()

        val tos: String
            get() = URLBuilder().apply {
                val url = Url(website)
                host = url.host
                protocol = url.protocol
                pathSegments = url.rawSegments + TOS_PATH
            }.buildString()
    }

    @Serializable
    public data class MetaData(
        @SerialName("federation") val federation: Boolean,
        @SerialName("commonApiVersion")
        @Serializable(CommonApiVersionTypeSerializer::class)
        val commonApiVersion: CommonApiVersionType,
        @SerialName("domain") val domain: String?
    )

    @Serializable
    public data class VersionInfo(
        @SerialName("federation") val federation: Boolean,
        @SerialName("supported") val supported: List<Int>,
        @SerialName("domain") val domain: String? = null,
        @SerialName("development") val developmentSupported: List<Int>? = null,
    )

    @Serializable
    public data class ApiProxy(
        @SerialName("needsAuthentication") val needsAuthentication: Boolean,
        @SerialName("host") val host: String,
        @SerialName("port") val port: Int
    )

    public companion object {
        public val PRODUCTION: Links = Links(
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

        public val STAGING: Links = Links(
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

        public val DUMMY: Links = Links(
            api = """https://dummy-nginz-https.zinfra.io""",
            accounts = """https://wire-account-dummy.zinfra.io""",
            webSocket = """https://dummy-nginz-ssl.zinfra.io""",
            teams = """https://wire-teams-dummy.zinfra.io""",
            blackList = """https://clientblacklist.wire.com/dummy""",
            website = """https://dummy.wire.com""",
            title = "dummy",
            isOnPremises = false,
            apiProxy = null
        )

        public val DEFAULT: Links = PRODUCTION

        private const val FORGOT_PASSWORD_PATH = "forgot"
        private const val PRICING_PATH = "pricing"
        private const val TOS_PATH = "legal"
    }
}

public sealed interface CommonApiVersionType {
    public val version: Int

    public data object New : CommonApiVersionType {
        override val version: Int
            get() = NEW_API_VERSION_NUMBER
    }

    public data object Unknown : CommonApiVersionType {
        override val version: Int
            get() = UNKNOWN_API_VERSION_NUMBER
    }

    public data class Valid(override val version: Int) : CommonApiVersionType

    public companion object {
        public const val NEW_API_VERSION_NUMBER: Int = -1
        public const val UNKNOWN_API_VERSION_NUMBER: Int = -2
        public const val MINIMUM_VALID_API_VERSION: Int = 0
    }
}

fun Int?.toCommonApiVersionType() = when {
    this != null && this >= CommonApiVersionType.MINIMUM_VALID_API_VERSION -> CommonApiVersionType.Valid(this)
    this == CommonApiVersionType.NEW_API_VERSION_NUMBER -> CommonApiVersionType.New
    else -> CommonApiVersionType.Unknown
}

@OptIn(ExperimentalSerializationApi::class)
@Serializer(CommonApiVersionType::class)
internal class CommonApiVersionTypeSerializer : KSerializer<CommonApiVersionType> {
    override val descriptor = PrimitiveSerialDescriptor("common_api_version", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: CommonApiVersionType) {
        encoder.encodeInt(value.version)
    }

    override fun deserialize(decoder: Decoder): CommonApiVersionType = decoder.decodeInt().toCommonApiVersionType()
}
