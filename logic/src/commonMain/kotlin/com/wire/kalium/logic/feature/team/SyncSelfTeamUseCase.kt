package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.flow.first

internal interface SyncSelfTeamUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncSelfTeamUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository
) : SyncSelfTeamUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> {
        val user = userRepository.getSelfUser().first()

        return user.team?.let { teamId ->
            teamRepository.fetchTeamById(teamId = teamId)
            teamRepository.fetchMembersByTeamId(
                teamId = teamId,
                userDomain = user.id.domain
            )
        } ?: run {
            kaliumLogger.i("Skipping team sync because user doesn't belong to a team")
            Either.Right(Unit)
        }
    }
}
