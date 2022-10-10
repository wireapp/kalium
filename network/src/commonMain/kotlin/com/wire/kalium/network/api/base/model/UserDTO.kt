package com.wire.kalium.network.api.base.model

import com.wire.kalium.network.api.base.unauthenticated.register.NewBindingTeamDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDTO(
    @SerialName("accent_id") val accentId: Int,
    @SerialName("assets") val assets: List<UserAssetDTO>,
    @SerialName("deleted") val deleted: Boolean?,
    @SerialName("email") val email: String?,
    @SerialName("expires_at") val expiresAt: String?,
    @SerialName("handle") val handle: String?,
    @Deprecated("use id instead", replaceWith = ReplaceWith("this.id"))
    @SerialName("id") val nonQualifiedId: NonQualifiedUserId,
    @SerialName("name") val name: String,
    @SerialName("locale") val locale: String,
    @SerialName("managed_by") val managedByDTO: ManagedByDTO?,
    @SerialName("phone") val phone: String?,
    @SerialName("qualified_id") val id: UserId,
    @SerialName("service") val service: ServiceDTO?,
    @SerialName("sso_id") val ssoID: UserSsoIdDTO?,
    @SerialName("team") val teamId: TeamId?
)

@Serializable
internal data class NewUserDTO(
    @SerialName("accent_id") val accentId: Int?,
    @SerialName("assets") val assets: List<UserAssetDTO>?,
    @SerialName("email") val email: String?,
    @SerialName("email_code") val emailCode: String?,
    @SerialName("expires_in") val expiresIn: Int?,
    @SerialName("invitation_code") val invitationCode: String?, // Mutually exclusive with team|team_code ,
    // An optional label to associate with the access cookie, if one is granted during account creation.
    @SerialName("label") val label: String?,
    @SerialName("locale") val locale: String?,
    @SerialName("managed_by") val managedByDTO: ManagedByDTO?,
    @SerialName("name") val name: String,
    @SerialName("password") val password: String?,
    @SerialName("phone") val phone: String?,
    @SerialName("phone_code") val phoneCode: String?,
    @SerialName("sso_id") val ssoID: UserSsoIdDTO?,
    @SerialName("team") val newBindingTeamDTO: NewBindingTeamDTO?,
    @SerialName("team_code") val teamCode: String?,
    @SerialName("team_id") val teamID: TeamId?,
    @SerialName("uuid") val uuid: String?
)

@Serializable
enum class ManagedByDTO {
    @SerialName("wire")
    WIRE,

    @SerialName("scim")
    SCIM;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
