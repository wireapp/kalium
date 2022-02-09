package com.wire.kalium.network.api.user.client

import com.wire.kalium.network.api.model.LocationResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientResponse(
    @SerialName("cookie") val cookie: String?,
    @SerialName("time") val registrationTime: String, // yyyy-mm-ddThh:MM:ss.qqq
    @SerialName("location") val location: LocationResponse?,
    @SerialName("model") val model: String?,
    @SerialName("id") val clientId: String,
    @SerialName("type") val type: ClientTypeDTO,
    @SerialName("class") val deviceType: DeviceTypeDTO?,
    @SerialName("capabilities") val capabilities: Capabilities?,
    @SerialName("label") val label: String?
)

@Serializable
data class EventClientResponse(
    @SerialName("id") val id: String,
    @SerialName("cookie") val refreshToken: String,
    @SerialName("time") val registrationTime: String,
    @SerialName("location") val location: LocationResponse?,
    @SerialName("address") val ipAddress: String?,
    @SerialName("model") val model: String?,
    @SerialName("type") val deviceType: String,
    @SerialName("class") val deviceClass: String,
    @SerialName("label") val label: String?
)

@Serializable
data class ClientsOfUsersResponse(
    @SerialName("qualified_user_map") val qualifiedMap: Map<String, Map<String, List<SimpleClientResponse>>>
)

@Serializable
data class SimpleClientResponse(
    @SerialName("id") val id: String,
    @SerialName("class") val deviceClass: String
)

