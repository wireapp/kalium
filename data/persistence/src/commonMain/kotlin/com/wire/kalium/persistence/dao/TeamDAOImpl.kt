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

package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.TeamsQueries
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import com.wire.kalium.persistence.Team as SQLDelightTeam

class TeamMapper {
    fun toModel(team: SQLDelightTeam): TeamEntity = with(team) {
        TeamEntity(
            id = id,
            name = name,
            icon = icon
        )
    }
}

class TeamDAOImpl(
    private val queries: TeamsQueries,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) : TeamDAO {

    val mapper = TeamMapper()

    override suspend fun insertTeam(team: TeamEntity) {
        withContext(writeDispatcher.value) {
            queries.insertTeam(
                id = team.id,
                name = team.name,
                icon = team.icon
            )
        }
    }

    override suspend fun insertTeams(teams: List<TeamEntity>) = withContext(writeDispatcher.value) {
        queries.transaction {
            for (team: TeamEntity in teams) {
                queries.insertTeam(
                    id = team.id,
                    name = team.name,
                    icon = team.icon
                )
            }
        }
    }

    override suspend fun getTeamById(teamId: String) = queries.selectTeamById(id = teamId)
        .asFlow()
        .mapToOneOrNull()
        .map { it?.let { mapper.toModel(team = it) } }
        .flowOn(readDispatcher.value)

    override suspend fun updateTeam(team: TeamEntity) {
        withContext(writeDispatcher.value) {
            queries.updateTeam(
                id = team.id,
                name = team.name,
                icon = team.icon
            )
        }
    }
}
