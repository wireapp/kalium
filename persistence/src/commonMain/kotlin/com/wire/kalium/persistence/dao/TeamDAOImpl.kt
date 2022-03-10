package com.wire.kalium.persistence.dao

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.db.TeamsQueries
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Team as SQLDelightTeam

class TeamMapper {
    fun toModel(team: SQLDelightTeam): TeamEntity = with(team) {
        TeamEntity(
            id = id,
            name = name
        )
    }
}

class TeamDAOImpl(private val queries: TeamsQueries) : TeamDAO {

    val mapper = TeamMapper()

    override suspend fun insertTeam(team: TeamEntity) = queries.insertTeam(
        id = team.id,
        name = team.name
    )

    override suspend fun insertTeams(teams: List<TeamEntity>) = queries.transaction {
        for (team: TeamEntity in teams) {
            queries.insertTeam(
                id = team.id,
                name = team.name
            )
        }
    }

    override suspend fun getTeamById(teamId: String) = queries.selectTeamById(id = teamId)
        .asFlow()
        .mapToOneOrNull()
        .map { it?.let { mapper.toModel(team = it) } }
}
