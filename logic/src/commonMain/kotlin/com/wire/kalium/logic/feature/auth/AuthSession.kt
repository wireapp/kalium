package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    @SerialName("auth_tokens") val token: Token,
    @SerialName("server_links") val serverLinks: ServerConfig.Links,
) {
    @Serializable
    sealed class Token {
        abstract val userId: UserId
        abstract val accessToken: String
        abstract val refreshToken: String
        abstract val tokenType: String

        @Serializable
        @SerialName("auth_tokens.valid")
        data class Valid(
            @SerialName("user_id") override val userId: QualifiedID,
            @SerialName("access_token") override val accessToken: String,
            @SerialName("refresh_token") override val refreshToken: String,
            @SerialName("access_token_type") override val tokenType: String
        ) : Token()

        @Serializable
        @SerialName("authsession.invalid")
        data class Invalid(
            @SerialName("user_id") override val userId: QualifiedID,
            @SerialName("access_token") override val accessToken: String,
            @SerialName("refresh_token") override val refreshToken: String,
            @SerialName("access_token_type") override val tokenType: String,
            @SerialName("reason") val reason: LogoutReason,
            @SerialName("hardLogout") val hardLogout: Boolean
        ) : Token()
    }
}
