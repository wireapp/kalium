package com.wire.kalium.persistence.dao

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.TeamsQueries
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
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
    private val coroutineContext: CoroutineContext
) : TeamDAO {

    val mapper = TeamMapper()

    override suspend fun insertTeam(team: TeamEntity) = withContext(coroutineContext) {
        queries.insertTeam(
            id = team.id,
            name = team.name,
            icon = team.icon
        )
    }

    override suspend fun insertTeams(teams: List<TeamEntity>) = withContext(coroutineContext) {
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
        .flowOn(coroutineContext)
        .mapToOneOrNull()
        .map { it?.let { mapper.toModel(team = it) } }

    override suspend fun updateTeam(team: TeamEntity) = withContext(coroutineContext) {
        queries.updateTeam(
            id = team.id,
            name = team.name,
            icon = team.icon
        )
    }
}
