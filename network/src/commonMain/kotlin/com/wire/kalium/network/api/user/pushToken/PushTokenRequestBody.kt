package com.wire.kalium.network.api.user.pushToken


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PushTokenRequestBody(
    @SerialName("app")
    val senderId: String,
    @SerialName("client")
    val client: String,
    @SerialName("token")
    val token: String,
    @SerialName("transport")
    val transport: String
)
