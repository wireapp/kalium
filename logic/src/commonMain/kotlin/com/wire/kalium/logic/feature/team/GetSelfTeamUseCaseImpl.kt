package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

fun interface GetSelfTeamUseCase {
    suspend operator fun invoke(): Flow<Team?>
}

@OptIn(ExperimentalCoroutinesApi::class)
class GetSelfTeamUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
) : GetSelfTeamUseCase {
    override suspend operator fun invoke(): Flow<Team?> {
        return userRepository.observeSelfUser()
            .flatMapLatest {
                if (it.teamId != null) teamRepository.getTeam(it.teamId)
                else flow { emit(it.teamId) }
            }
    }
}
