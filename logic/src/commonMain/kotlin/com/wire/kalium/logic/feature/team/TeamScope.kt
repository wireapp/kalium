package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.self.SelfUserRepository
import com.wire.kalium.logic.sync.SyncManager

class TeamScope(
    private val selfUserRepository: SelfUserRepository,
    private val teamRepository: TeamRepository,
    private val syncManager: SyncManager,
) {
    internal val syncSelfTeamUseCase: SyncSelfTeamUseCase get() = SyncSelfTeamUseCaseImpl(
        userRepository = selfUserRepository,
        teamRepository = teamRepository
    )

    val getSelfTeamUseCase: GetSelfTeamUseCase get() = GetSelfTeamUseCase(
        userRepository = selfUserRepository,
        teamRepository = teamRepository,
        syncManager = syncManager
    )
}
