package com.wire.kalium.logic.data.team

import com.wire.kalium.network.api.model.TeamDTO
import com.wire.kalium.persistence.dao.TeamEntity

interface TeamMapper {
    fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity
}

internal class TeamMapperImpl : TeamMapper {

    override fun fromDtoToEntity(teamDTO: TeamDTO): TeamEntity =
        TeamEntity(teamDTO.id, teamDTO.name)
}
