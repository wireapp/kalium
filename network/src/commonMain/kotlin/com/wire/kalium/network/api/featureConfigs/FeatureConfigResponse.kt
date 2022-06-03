package com.wire.kalium.network.api.featureConfigs


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureConfigResponse(
    @SerialName("lockStatus")
    val lockStatus: String,
    @SerialName("status")
    val status: String
)
