package com.wire.kalium.persistence.dao

data class TeamEntity(
    val id: String,
    val name: String?
)

interface TeamDAO {
    suspend fun insertTeam(team: TeamEntity)
    suspend fun insertTeams(teams: List<TeamEntity>)
    suspend fun getTeamById(teamId: String)
    suspend fun getTeamsById(teamId: String)
}
