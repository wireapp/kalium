package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class AccessTokenDTO (
    @SerialName("access_token")
    val value: String,
    @SerialName("expires_in")
    val expiresIn: Int,
    @SerialName("token_type")
    val tokenType: String
)
