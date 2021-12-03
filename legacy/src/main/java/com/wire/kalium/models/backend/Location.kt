package com.wire.kalium.models.backend

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Deprecated("Use LocationResponse", ReplaceWith("com.wire.kalium.api.user.client.LocationResponse"))
@Serializable
data class Location(
        @SerialName("lat") val lat: Double,
        @SerialName("lon") val lon: Double
)
