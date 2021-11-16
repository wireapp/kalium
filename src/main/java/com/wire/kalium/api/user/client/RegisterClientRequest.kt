package com.wire.kalium.api.user.client

import com.wire.kalium.LoginClient.Location
import com.wire.kalium.models.outbound.otr.PreKey
import kotlinx.serialization.SerialName

data class RegisterClientRequest(
        @SerialName("password") val password: String,
        @SerialName("class") val deviceType: String,
        @SerialName("type") val type: String,
        @SerialName("label") val label: String,
        @SerialName("prekeys") val preKeys: List<PreKey>,
        @SerialName("lastkey") val lastKey: PreKey,
)

data class RegisterClientResponse(
        @SerialName("id") val clientId: String,
        @SerialName("tile") val time: String,
        @SerialName("location") val location: Location,
        @SerialName("type") val type: String,
        @SerialName("class") val deviceType: String,
        @SerialName("label") val label: String,
        val capabilities: Capabilities
)

data class Capabilities(
        @SerialName("capabilities") val capabilities: List<String>
)
