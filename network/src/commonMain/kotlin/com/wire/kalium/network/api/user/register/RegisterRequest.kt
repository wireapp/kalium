package com.wire.kalium.network.api.user.register


import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.AssetKey
import com.wire.kalium.network.api.model.UserAsset
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RegisterRequest(
    @SerialName("accent_id")
    val accentId: Int?,
    @SerialName("assets")
    val assets: List<UserAsset>?,
    @SerialName("email")
    val email: String?,
    @SerialName("email_code")
    val emailCode: String?,
    @SerialName("invitation_code")
    val invitationCode: String?, // Mutually exclusive with team|team_code ,
    @SerialName("label")
    val label: String?, // An optional label to associate with the access cookie, if one is granted during account creation.
    @SerialName("locale")
    val locale: String?,
    @SerialName("name")
    val name: String,
    @SerialName("password")
    val password: String?,
    @SerialName("phone")
    val phone: String,
    @SerialName("phone_code")
    val phoneCode: String?,
    @SerialName("team")
    val newBindingTeam: NewBindingTeam?,
    @SerialName("team_code")
    val teamCode: String?
)

@Serializable
data class NewBindingTeam(
    @SerialName("icon")
    val iconAssetId: AssetId,
    @SerialName("icon_key")
    val iconKey: AssetKey,
    @SerialName("name")
    val name: String
)
