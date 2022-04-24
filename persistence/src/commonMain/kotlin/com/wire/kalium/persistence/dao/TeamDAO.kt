package com.wire.kalium.persistence.dao

data class TeamEntity(
    val id: String,
    val name: String?
)

interface TeamDAO {
    fun insertTeam(team: TeamEntity)
    fun insertTeams(teams: List<TeamEntity>)
    fun getTeamById(teamId: String): TeamEntity?
}
