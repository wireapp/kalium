package com.wire.kalium.api.auth

import kotlinx.serialization.SerialName

data class RenewAccessTokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Int,
        @SerialName("token_type") val tokenType: String,
)
