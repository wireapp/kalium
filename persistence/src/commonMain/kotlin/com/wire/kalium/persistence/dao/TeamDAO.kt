package com.wire.kalium.persistence.dao

import kotlinx.coroutines.flow.Flow

data class TeamEntity(
    val id: String,
    val name: String?
)

interface TeamDAO {
    suspend fun insertTeam(team: TeamEntity)
    suspend fun insertTeams(teams: List<TeamEntity>)
    suspend fun getTeamById(teamId: String): Flow<TeamEntity?>
}
