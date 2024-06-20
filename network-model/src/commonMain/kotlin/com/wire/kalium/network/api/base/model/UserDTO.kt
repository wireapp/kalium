/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.base.model

import com.wire.kalium.network.api.base.unauthenticated.register.NewBindingTeamDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed class UserDTO {
    abstract val id: UserId
    abstract val name: String
    abstract val handle: String?
    abstract val teamId: TeamId?
    abstract val accentId: Int
    abstract val assets: List<UserAssetDTO>
    abstract val deleted: Boolean?
    abstract val email: String?
    abstract val expiresAt: String?
    abstract val nonQualifiedId: NonQualifiedUserId
    abstract val service: ServiceDTO?
    abstract val supportedProtocols: List<SupportedProtocolDTO>?
}

@Serializable
data class UserProfileDTO(
    @SerialName("qualified_id") override val id: UserId,
    @SerialName("name") override val name: String,
    @SerialName("handle") override val handle: String?,
    @SerialName("team") override val teamId: TeamId?,
    @SerialName("accent_id") override val accentId: Int,
    @SerialName("assets") override val assets: List<UserAssetDTO>,
    @SerialName("deleted") override val deleted: Boolean?,
    @SerialName("email") override val email: String?,
    @SerialName("expires_at") override val expiresAt: String?,
    @Deprecated("use id instead", replaceWith = ReplaceWith("this.id"))
    @SerialName("id") override val nonQualifiedId: NonQualifiedUserId,
    @SerialName("service") override val service: ServiceDTO?,
    @SerialName("supported_protocols") override val supportedProtocols: List<SupportedProtocolDTO>?,
    @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusDTO,
) : UserDTO()

fun UserProfileDTO.isTeamMember(selfUserTeamId: String?, selfUserDomain: String?) =
    (selfUserTeamId != null && this.teamId == selfUserTeamId && this.id.domain == selfUserDomain)

@Serializable
data class SelfUserDTO(
    @SerialName("qualified_id") override val id: UserId,
    @SerialName("name") override val name: String,
    @SerialName("handle") override val handle: String?,
    @SerialName("team") override val teamId: TeamId?,
    @SerialName("accent_id") override val accentId: Int,
    @SerialName("assets") override val assets: List<UserAssetDTO>,
    @SerialName("deleted") override val deleted: Boolean?,
    @SerialName("email") override val email: String?,
    @SerialName("expires_at") override val expiresAt: String?,
    @Deprecated("use id instead", replaceWith = ReplaceWith("this.id"))
    @SerialName("id") override val nonQualifiedId: NonQualifiedUserId,
    @SerialName("service") override val service: ServiceDTO?,
    @SerialName("supported_protocols") override val supportedProtocols: List<SupportedProtocolDTO>?,
    @SerialName("locale") val locale: String,
    @SerialName("managed_by") val managedByDTO: ManagedByDTO?,
    @SerialName("phone") val phone: String?,
    @SerialName("sso_id") val ssoID: UserSsoIdDTO?
) : UserDTO()

@Serializable
data class NewUserDTO(
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

@Serializable
enum class SupportedProtocolDTO {
    @SerialName("proteus")
    PROTEUS,

    @SerialName("mls")
    MLS;

    override fun toString(): String {
        return this.name.lowercase()
    }
}
