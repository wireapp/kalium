/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.team

import com.wire.kalium.network.api.model.TeamDTO
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
