package com.wire.kalium.network.api.asset

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

@Serializable
enum class ImageSize {
    @SerialName("preview")
    Preview,

    @SerialName("complete")
    Complete;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

fun List<UserAssetDTO>?.getPreviewAssetOrNull() = this?.firstOrNull { it.size == ImageSize.Preview }
fun List<UserAssetDTO>?.getCompleteAssetOrNull() = this?.firstOrNull { it.size == ImageSize.Complete }
