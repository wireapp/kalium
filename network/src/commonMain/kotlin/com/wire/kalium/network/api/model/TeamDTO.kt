package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.TeamId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamDTO(
    @SerialName("creator") val creator: String,
    @SerialName("icon") val icon: String,
    @SerialName("name") val name: String,
    @SerialName("id") val id: TeamId,
    @SerialName("icon_key") val iconKey: String?,
    @SerialName("binding") val binding: Boolean?
)
