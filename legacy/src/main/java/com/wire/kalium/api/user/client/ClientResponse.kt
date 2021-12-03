package com.wire.kalium.api.user.client

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientResponse(
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
data class LocationResponse(
    // TODO: check if location name is needed
    //@SerialName("name") val name: String,
    @SerialName("lat") val latitude: String,
    @SerialName("lon") val longitude: String
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

typealias RemainingPreKeysResponse = List<Int>
