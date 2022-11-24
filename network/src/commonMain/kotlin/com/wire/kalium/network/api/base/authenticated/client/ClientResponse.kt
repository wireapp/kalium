package com.wire.kalium.network.api.base.authenticated.client

import com.wire.kalium.network.api.base.model.LocationResponse
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
    @SerialName("class") val deviceType: DeviceTypeDTO = DeviceTypeDTO.Unknown,
    @SerialName("capabilities") val capabilities: Capabilities?,
    @SerialName("label") val label: String?,
    @SerialName("mls_public_keys") val mlsPublicKeys: Map<String, String>?
)

@Serializable
data class ClientsOfUsersResponse(
    @SerialName("qualified_user_map") val qualifiedMap: Map<String, Map<String, List<SimpleClientResponse>>>
)

@Serializable
data class SimpleClientResponse(
    @SerialName("id") val id: String,
    @SerialName("class") val deviceClass: DeviceTypeDTO = DeviceTypeDTO.Unknown
)
