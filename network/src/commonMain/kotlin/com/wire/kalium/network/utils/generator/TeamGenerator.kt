package com.wire.kalium.network.utils.generator

import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.network.api.teams.TeamsApi.TeamMember
import com.wire.kalium.network.api.teams.TeamsApi.Permissions
import com.wire.kalium.network.api.user.LegalHoldStatusResponse

object TeamGenerator {

    fun createTeam(
        creator: String = "creator",
        icon: AssetId = "assetId",
        name: String = "name",
        id: TeamId = "teamId",
        iconKey: AssetId? = null,
        binding: Boolean? = false
    ): TeamDTO = TeamDTO(
        creator = creator,
        icon = icon,
        name = name,
        id = id,
        iconKey = iconKey,
        binding = binding
    )

    fun createTeamMember(
        nonQualifiedUserId: NonQualifiedUserId = "id",
        createdBy: NonQualifiedUserId? = null,
        legalHoldStatus: LegalHoldStatusResponse? = null,
        createdAt: String? = null,
        permissions: Permissions? = null
    ): TeamMember = TeamMember(
        nonQualifiedUserId = nonQualifiedUserId,
        createdBy = createdBy,
        legalHoldStatus = legalHoldStatus,
        createdAt = createdAt,
        permissions = permissions
    )
}
