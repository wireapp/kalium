package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.network.api.AssetId
import com.wire.kalium.network.api.NonQualifiedUserId
import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.network.api.user.LegalHoldStatusResponse
import com.wire.kalium.network.api.TeamId as TeamIdDTO

object TestTeam {
    val TEAM_ID = TeamId("Some-Team")

    fun dto(
        creator: String = "creator",
        icon: AssetId = "assetId",
        name: String = "name",
        id: TeamIdDTO = "teamId",
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

    fun memberDTO(
        nonQualifiedUserId: NonQualifiedUserId = "id",
        createdBy: NonQualifiedUserId? = null,
        legalHoldStatus: LegalHoldStatusResponse? = null,
        createdAt: String? = null,
        permissions: TeamsApi.Permissions? = null
    ): TeamsApi.TeamMemberDTO = TeamsApi.TeamMemberDTO(
        nonQualifiedUserId = nonQualifiedUserId,
        createdBy = createdBy,
        legalHoldStatus = legalHoldStatus,
        createdAt = createdAt,
        permissions = permissions
    )
}
