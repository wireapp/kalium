package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.model.UserAssetDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserUpdateRequest(
    @SerialName("name")
    val name: String?,
    @SerialName("assets")
    val assets: List<UserAssetDTO>?,
    @SerialName("accent_id")
    val accentId: Int?
)
