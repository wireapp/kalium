package com.wire.kalium.logic.feature.team

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal interface SyncSelfTeamUseCase {
    suspend operator fun invoke(): Either<CoreFailure, Unit>
}

internal class SyncSelfTeamUseCaseImpl(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : SyncSelfTeamUseCase {

    override suspend fun invoke(): Either<CoreFailure, Unit> = withContext(dispatchers.default) {
        val user = userRepository.observeSelfUser().first()

        user.teamId?.let { teamId ->
            teamRepository.fetchTeamById(teamId = teamId)
            teamRepository.fetchMembersByTeamId(
                teamId = teamId,
                userDomain = user.id.domain
            )
        } ?: run {
            kaliumLogger.withFeatureId(SYNC).i("Skipping team sync because user doesn't belong to a team")
            Either.Right(Unit)
        }
    }
}
