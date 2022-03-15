package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.TeamId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamDTO(
    val creator: String,
    val icon: AssetId,
    val name: String,
    val id: TeamId,
    @SerialName("icon_key") val iconKey: AssetId?,
    val binding: Boolean?
)
