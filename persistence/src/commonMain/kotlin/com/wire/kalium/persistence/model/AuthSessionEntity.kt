package com.wire.kalium.persistence.model

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServerConfigEntity(
    @SerialName("id") val id: String,
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
        @SerialName("title") val title: String
    )

    @Serializable
    data class MetaData(
        @SerialName("federation") val federation: Boolean,
        @SerialName("commonApiVersion") val apiVersion: Int,
        @SerialName("domain") val domain: String?
    )
}

@Serializable
sealed class AuthSessionEntity(@SerialName("user_id") open val userId: QualifiedIDEntity) {
    @Serializable
    @SerialName("authsession.valid")
    data class Valid(
        override val userId: QualifiedIDEntity,
        @SerialName("token_type") val tokenType: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("refresh_token") val refreshToken: String,
        @SerialName("wire_server") val serverLinks: ServerConfigEntity.Links
    ) : AuthSessionEntity(userId)

    @Serializable
    @SerialName("authsession.invalid")
    data class Invalid(
        override val userId: QualifiedIDEntity,
        @SerialName("wire_server") val serverLinks: ServerConfigEntity.Links,
        @SerialName("reason") val reason: LogoutReason,
        @SerialName("hardLogout") val hardLogout: Boolean,
    ) : AuthSessionEntity(userId)
}
