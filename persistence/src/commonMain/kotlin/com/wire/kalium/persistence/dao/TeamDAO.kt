package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class TeamEntity(
    val id: String,
    val name: String,
    val icon: String
)

interface TeamDAO {
    suspend fun insertTeam(team: TeamEntity)
    suspend fun insertTeams(teams: List<TeamEntity>)
    suspend fun getTeamById(teamId: String): Flow<TeamEntity?>
    suspend fun updateTeam(team: TeamEntity)
}
