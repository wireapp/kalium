package com.wire.kalium.network.api.notification.team

import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.teams.TeamsApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TeamUpdateData(
    @SerialName("icon") val icon: String,
    @SerialName("name") val name: String,
)

@Serializable
data class TeamMemberIdData(
    @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId,
)

@Serializable
data class PermissionsData(
    @SerialName("permissions") val permissions: TeamsApi.Permissions,
    @SerialName("user") val nonQualifiedUserId: NonQualifiedUserId
)
