package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager

class TeamScope(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val syncManager: SyncManager,
) {
    internal val syncSelfTeamUseCase: SyncSelfTeamUseCase get() = SyncSelfTeamUseCaseImpl(
        userRepository = userRepository,
        teamRepository = teamRepository
    )

    val getSelfTeamUseCase: GetSelfTeamUseCase get() = GetSelfTeamUseCase(
        userRepository = userRepository,
        teamRepository = teamRepository,
        syncManager = syncManager
    )
}
