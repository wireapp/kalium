package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.network.api.base.model.NonQualifiedUserId
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse
import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.network.api.base.model.TeamId as TeamIdDTO

@Suppress("LongParameterList")
object TestTeam {
    val TEAM: Team = Team("Some-Team", "Some-name")
    val TEAM_ID = TeamId("Some-Team")

    fun dto(
        creator: String = "creator",
        icon: String = "value1",
        name: String = "name",
        id: TeamIdDTO = "teamId",
        iconKey: String? = null,
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
