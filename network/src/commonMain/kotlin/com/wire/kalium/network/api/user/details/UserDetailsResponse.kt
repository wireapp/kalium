package com.wire.kalium.network.api.user.details

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.Asset
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDetailsResponse(
    @SerialName("qualified_id") val id: UserId,
    @SerialName("name") val name: String,
    @SerialName("handle") val handle: String,
    @SerialName("legalhold_status") val legalHoldStatus: LegalHoldStatusResponse,
    @SerialName("team") val team: String?,
    @SerialName("accent_id") val accentId: Int,
    @SerialName("assets") val assets: List<Asset>
)
