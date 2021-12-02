package com.wire.kalium.network.api.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RenewAccessTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)
