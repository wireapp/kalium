package com.wire.kalium.persistence.dao

import com.wire.kalium.persistence.TeamsQueries
import com.wire.kalium.persistence.Team as SQLDelightTeam

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

    override fun insertTeam(team: TeamEntity) = queries.insertTeam(
        id = team.id,
        name = team.name
    )

    override fun insertTeams(teams: List<TeamEntity>) = queries.transaction {
        for (team: TeamEntity in teams) {
            queries.insertTeam(
                id = team.id,
                name = team.name
            )
        }
    }

    override fun getTeamById(teamId: String): TeamEntity? = queries.selectTeamById(teamId).executeAsOneOrNull()?.let { mapper.toModel(it) }
}
