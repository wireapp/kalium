package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAssetDTO(
    @SerialName("key")
    val key: String,
    @SerialName("size")
    val size: AssetSizeDTO?,
    @SerialName("type")
    val type: UserAssetTypeDTO
)

fun List<UserAssetDTO>?.getPreviewAssetOrNull() = this?.firstOrNull { it.size == AssetSizeDTO.PREVIEW }
fun List<UserAssetDTO>?.getCompleteAssetOrNull() = this?.firstOrNull { it.size == AssetSizeDTO.COMPLETE }

@Serializable
enum class AssetSizeDTO {
    @SerialName("preview")
    PREVIEW,

    @SerialName("complete")
    COMPLETE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
enum class UserAssetTypeDTO {
    @SerialName("image")
    IMAGE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
