package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.functional.Either

class GetTeamUseCase(private val teamRepository: TeamRepository) {

    suspend operator fun invoke(): Either<CoreFailure, Unit> {
        return teamRepository.getTeam()
    }
}
