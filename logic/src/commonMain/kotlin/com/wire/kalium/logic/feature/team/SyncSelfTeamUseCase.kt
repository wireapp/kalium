package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.first

internal interface SyncSelfTeamUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncSelfTeamUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) : SyncSelfTeamUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> =
        userRepository.getSelfUser().first().team?.let { teamId ->
            teamRepository.fetchTeamById(teamId = teamId)
        } ?: Either.Right(Unit)
}
