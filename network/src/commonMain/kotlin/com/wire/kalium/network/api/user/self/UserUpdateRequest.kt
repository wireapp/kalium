package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    @SerialName("id")
    val id: String,
    @SerialName("qualified_id")
    val qualifiedId: UserId,
    @SerialName("name")
    val name: String?,
    @SerialName("assets")
    val assets: List<UserAssetRequest>,
    @SerialName("accent_id")
    val accentId: Int?
) {
    @SerialName("picture")
    var picture: List<UserAssetRequest> = assets
}

@Serializable
data class UserAssetRequest(
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
