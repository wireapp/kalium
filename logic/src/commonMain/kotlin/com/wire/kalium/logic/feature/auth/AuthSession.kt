package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.logout.LogoutReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    @SerialName("auth_tokens") val session: Session,
    @SerialName("server_links") val serverLinks: ServerConfig.Links,
) {
    @Serializable
    sealed class Session(open val userId: QualifiedID) {
        @Serializable
        @SerialName("auth_tokens.valid")
        data class Valid(
            @SerialName("user_id") override val userId: QualifiedID,
            @SerialName("access_token") val accessToken: String,
            @SerialName("refresh_token") val refreshToken: String,
            @SerialName("access_token_type") val tokenType: String
        ) : Session(userId)

        @Serializable
        @SerialName("authsession.invalid")
        data class Invalid(
            @SerialName("user_id") override val userId: QualifiedID,
            @SerialName("reason") val reason: LogoutReason,
            @SerialName("hardLogout") val hardLogout: Boolean
        ) : Session(userId)
    }
}
