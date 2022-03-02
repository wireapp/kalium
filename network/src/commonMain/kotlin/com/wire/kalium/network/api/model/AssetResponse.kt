package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AssetResponse(
    @SerialName("key") val key: String,
    @SerialName("expires") val expires: String?,
    @SerialName("token") val token: String?
)
