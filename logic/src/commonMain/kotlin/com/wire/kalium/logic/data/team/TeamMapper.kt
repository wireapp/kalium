package com.wire.kalium.logic.data.team

import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.persistence.dao.TeamEntity

interface TeamMapper {
    fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity
    fun fromDaoModelToTeam(teamEntity: TeamEntity): Team
}

internal class TeamMapperImpl : TeamMapper {

    override fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity =
        TeamEntity(teamDTO.id, teamDTO.name)

    override fun fromDaoModelToTeam(teamEntity: TeamEntity): Team =
        Team(teamEntity.id, teamEntity.name)
}
