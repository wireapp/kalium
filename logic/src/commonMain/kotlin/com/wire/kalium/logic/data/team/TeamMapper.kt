package com.wire.kalium.logic.data.team

import com.wire.kalium.network.api.teams.TeamsApi
import com.wire.kalium.persistence.dao.TeamEntity

interface TeamMapper {
    fun fromApiModelToDaoModel(team: TeamsApi.Team): TeamEntity
}

internal class TeamMapperImpl: TeamMapper {

    override fun fromApiModelToDaoModel(team: TeamsApi.Team): TeamEntity =
        TeamEntity(
            id = team.id,
            name = team.name
        )
}
