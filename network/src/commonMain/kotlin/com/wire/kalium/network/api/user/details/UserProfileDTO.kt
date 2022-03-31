package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.ServiceDTO
import com.wire.kalium.network.api.model.UserAssetDTO
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfileDTO(
    @SerialName("qualified_id") val id: UserId,
    @SerialName("name") val name: String,
    @SerialName("handle") val handle: String?,
    @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusResponse,
    @SerialName("team") val teamId: TeamId?,
    @SerialName("accent_id") val accentId: Int,
    @SerialName("assets") val assets: List<UserAssetDTO>,
    @SerialName("deleted") val deleted: Boolean?,
    @SerialName("email") val email: String?,
    @SerialName("expires_at") val expiresAt: String?,
    @Deprecated("use id instead", replaceWith = ReplaceWith("this.id"))
    @SerialName("id") val nonQualifiedId: NonQualifiedUserId,
    @SerialName("service") val service: ServiceDTO?
)
