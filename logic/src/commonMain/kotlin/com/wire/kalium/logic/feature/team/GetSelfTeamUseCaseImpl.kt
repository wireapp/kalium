package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.Team
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for getting the team of the self user.
 * fixme: this can be replaced, since we are not using the team, but the team id, we can inject the team id directly
 * @see [SelfTeamIdProvider]
 */
fun interface GetSelfTeamUseCase {
    suspend operator fun invoke(): Flow<Team?>
}

@OptIn(ExperimentalCoroutinesApi::class)
class GetSelfTeamUseCaseImpl internal constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : GetSelfTeamUseCase {
    override suspend operator fun invoke(): Flow<Team?> = withContext(dispatchers.default) {
        userRepository.observeSelfUser()
            .flatMapLatest {
                if (it.teamId != null) teamRepository.getTeam(it.teamId)
                else flow { emit(it.teamId) }
            }
    }
}
