package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LocationResponse(
    @SerialName("lat") val latitude: String,
    @SerialName("lon") val longitude: String
)
