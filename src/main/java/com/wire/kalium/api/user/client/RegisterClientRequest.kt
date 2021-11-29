package com.wire.kalium.api.user.client

import com.wire.kalium.models.outbound.otr.PreKey
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterClientRequest(
        @SerialName("password") val password: String,
        @SerialName("class") val deviceType: DeviceType,
        @SerialName("type") val type: ClientType, // 'temporary', 'permanent', 'legalhold'
        @SerialName("label") val label: String,
        @SerialName("prekeys") val preKeys: List<PreKey>,
        @SerialName("lastkey") val lastKey: PreKey
)

@Serializable
data class RegisterClientResponse(
        @SerialName("id") val clientId: String,
        @SerialName("type") val type: ClientType,
        @SerialName("time") val registrationTime: String,
        @SerialName("location") val location: LocationResponse? = null,
        @SerialName("class") val deviceType: DeviceType?,
        @SerialName("label") val label: String?,
        val capabilities: Capabilities?
)

@Serializable
data class Capabilities(
        @SerialName("capabilities") val capabilities: List<String>
)

@Serializable
enum class ClientType {
    @SerialName("temporary")
    Temporary,
    @SerialName("permanent")
    Permanent,
    @SerialName("legalhold")
    LegalHold
}

@Serializable
enum class DeviceType {
    //'phone', 'tablet', 'desktop', 'legalhold'
    @SerialName("phone")
    Phone,
    @SerialName("tablet")
    Tablet,
    @SerialName("desktop")
    Desktop,
    @SerialName("legalhold")
    LegalHold
}
