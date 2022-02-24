package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.asset.UserAssetDTO
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
    val assets: List<UserAssetDTO>?,
    @SerialName("accent_id")
    val accentId: Int?
) {
    @SerialName("picture")
    var picture: List<UserAssetDTO>? = assets
}
