package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.TeamRepository

class TeamScope(
    private val teamRepository: TeamRepository
) {
    val syncTeam: GetTeamUseCase get() = GetTeamUseCase(teamRepository = teamRepository)
}
