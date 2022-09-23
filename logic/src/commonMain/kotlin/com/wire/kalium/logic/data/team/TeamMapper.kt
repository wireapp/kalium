package com.wire.kalium.logic.data.team

import com.wire.kalium.network.api.base.model.TeamDTO
import com.wire.kalium.persistence.dao.TeamEntity

interface TeamMapper {
    fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity
    fun fromModelToEntity(team: Team): TeamEntity
    fun fromDaoModelToTeam(teamEntity: TeamEntity): Team
}

internal class TeamMapperImpl : TeamMapper {

    override fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity =
        TeamEntity(teamDTO.id, teamDTO.name, teamDTO.icon)

    override fun fromModelToEntity(team: Team): TeamEntity =
        TeamEntity(
            id = team.id,
            name = team.name,
            icon = team.icon
        )

    override fun fromDaoModelToTeam(teamEntity: TeamEntity): Team =
        Team(teamEntity.id, teamEntity.name, teamEntity.icon)
}
