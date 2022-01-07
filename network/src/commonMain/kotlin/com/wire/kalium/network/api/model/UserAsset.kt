package com.wire.kalium.network.api.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserAsset(
    @SerialName("key")
    val key: String,
    @SerialName("size")
    val size: AssetSize?,
    @SerialName("type")
    val type: AssetType
)

@Serializable
enum class AssetSize {
    @SerialName("preview")
    Preview,

    @SerialName("complete")
    Complete;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
enum class AssetType {
    @SerialName("image")
    Image;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
