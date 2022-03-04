package com.wire.kalium.network.api.user.self

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.asset.AvatarAssetDTO
import com.wire.kalium.network.api.model.Service
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SelfUserInfoResponse(
    @SerialName("email")
    val email: String?,
    @SerialName("phone")
    val phone: String?,
    @SerialName("sso_id")
    val userSsoId: UserSsoId?,
    @SerialName("id")
    val id: String,
    @SerialName("qualified_id")
    val qualifiedId: UserId,
    @SerialName("name")
    val name: String,
    @SerialName("accent_id")
    val accentId: Int,
    @SerialName("deleted")
    val deleted: Boolean?,
    @SerialName("assets")
    val assets: List<AvatarAssetDTO>,
    @SerialName("locale")
    val locale: String,
    @SerialName("service")
    val service: Service?,
    @SerialName("expires_at")
    val expiresAt: String?, // Time of sending message. ,
    @SerialName("handle")
    val handle: String?,
    @SerialName("team")
    val team: String?,
    @SerialName("managed_by")
    val managedBy: ManagedBy // 'wire', 'scim'
) {
    @SerialName("picture")
    var picture: List<AvatarAssetDTO>? = assets
}

@Serializable
enum class ManagedBy {
    @SerialName("wire")
    Wire,

    @SerialName("scim")
    Scim;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
