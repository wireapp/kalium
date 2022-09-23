package com.wire.kalium.network.api.base.authenticated.userDetails

import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.ServiceDTO
import com.wire.kalium.network.api.base.model.UserAssetDTO
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
