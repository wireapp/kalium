package com.wire.kalium.network.api.user.login

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginWithEmailRequest(
    @SerialName("email") val email: String,
    @SerialName("password") val password: String,
    @SerialName("label") val label: String
)

@Serializable
data class LoginWithEmailResponse(
    @SerialName("user") val userId: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String
)
