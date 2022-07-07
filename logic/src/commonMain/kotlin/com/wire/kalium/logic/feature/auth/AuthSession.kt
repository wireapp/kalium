package com.wire.kalium.logic.feature.auth

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AuthSession(
    @SerialName("auth_tokens") val tokens: Tokens,
    @SerialName("server_links") val serverLinks: ServerConfig.Links
) {
    @Serializable
    sealed class Tokens {
        @Serializable
        @SerialName("auth_tokens.valid")
        data class Valid(
            @SerialName("user_id") val userId: QualifiedID,
            @SerialName("access_token") val accessToken: String,
            @SerialName("refresh_token") val refreshToken: String,
            @SerialName("access_token_type") val tokenType: String
        ) : Tokens()

        @Serializable
        @SerialName("auth_tokens.self_logout")
        data class SelfLogout(
            @SerialName("user_id") val userId: QualifiedID,
            @SerialName("hardLogout") val hardLogout: Boolean,
        ) : Tokens()

        @Serializable
        @SerialName("auth_tokens.user_deleted")
        data class UserDeleted(
            @SerialName("user_id") val userId: QualifiedID,
            @SerialName("hardLogout") val hardLogout: Boolean,
        ) : Tokens()

        @Serializable
        @SerialName("auth_tokens.removed_client")
        data class RemovedClient(
            @SerialName("user_id") val userId: QualifiedID,
            @SerialName("hardLogout") val hardLogout: Boolean,
        ) : Tokens()
    }


}
