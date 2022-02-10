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
    PREVIEW,

    @SerialName("complete")
    COMPLETE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
enum class AssetType {
    @SerialName("image")
    IMAGE;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
