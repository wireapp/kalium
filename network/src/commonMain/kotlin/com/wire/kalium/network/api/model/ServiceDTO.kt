package com.wire.kalium.network.api.model
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ServiceDTO(
    @SerialName("id")
    val id: String,
    @SerialName("provider")
    val provider: String
)
