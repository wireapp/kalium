package com.wire.kalium.logic.data.featureConfig


import com.wire.kalium.network.api.featureConfigs.ConfigsStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FeatureConfigEventResponse(
    @SerialName("data")
    val data: ConfigsStatus,
    @SerialName("name")
    val name: String,
    @SerialName("type")
    val type: String
)
