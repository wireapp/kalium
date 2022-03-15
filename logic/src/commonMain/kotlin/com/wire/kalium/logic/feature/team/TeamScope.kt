package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository

class TeamScope(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) {
    internal val syncSelfTeamUseCase: SyncSelfTeamUseCase get() = SyncSelfTeamUseCaseImpl(
        userRepository = userRepository,
        teamRepository = teamRepository
    )
}
