package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.user.self.ImageSize
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAssetDTO(
    @SerialName("key")
    val key: String,
    @SerialName("size")
    val size: ImageSize,
    @SerialName("type")
    val type: String = "image"
)
