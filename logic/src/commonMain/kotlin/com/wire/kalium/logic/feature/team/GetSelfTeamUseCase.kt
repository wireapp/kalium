package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

@OptIn(ExperimentalCoroutinesApi::class)
class GetSelfTeamUseCase(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val syncManager: SyncManager
) {
    suspend operator fun invoke(): Flow<Team?> {
        syncManager.waitForSyncToComplete()
        return userRepository.getSelfUser()
            .flatMapLatest {
                if (it.team != null) teamRepository.getTeam(it.team)
                else flow { emit(it.team) }
            }
    }
}
