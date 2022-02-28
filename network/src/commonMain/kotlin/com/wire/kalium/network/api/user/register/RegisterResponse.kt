package com.wire.kalium.network.api.user.register


import com.wire.kalium.network.api.model.Service
import com.wire.kalium.network.api.model.UserAsset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterResponse(
    @SerialName("accent_id")
    val accentId: Int?,
    @SerialName("assets")
    val assets: List<UserAsset>,
    @SerialName("deleted")
    val deleted: Boolean?,
    @SerialName("email")
    val email: String?,
    @SerialName("handle")
    val handle: String?,
    @SerialName("id")
    val id: String,
    @SerialName("name")
    val name: String,
    @SerialName("service")
    val service: Service?,
    @SerialName("team")
    val team: String?
)
