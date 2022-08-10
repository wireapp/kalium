package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository

class TeamScope internal constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
) {
    val getSelfTeamUseCase: GetSelfTeamUseCase
        get() = GetSelfTeamUseCaseImpl(
            userRepository = userRepository,
            teamRepository = teamRepository,
        )

    val deleteTeamConversationUseCase: DeleteTeamConversationUseCase
        get() = DeleteTeamConversationUseCaseImpl(
            getSelfTeam = getSelfTeamUseCase,
            teamRepository = teamRepository,
        )
}
