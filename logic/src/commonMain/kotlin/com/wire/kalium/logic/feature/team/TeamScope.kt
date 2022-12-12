package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase

class TeamScope internal constructor(
    private val userRepository: UserRepository,
    private val teamRepository: TeamRepository,
    private val conversationRepository: ConversationRepository,
    private val selfTeamIdProvider: SelfTeamIdProvider
) {
    val getSelfTeamUseCase: GetSelfTeamUseCase
        get() = GetSelfTeamUseCaseImpl(
            userRepository = userRepository,
            teamRepository = teamRepository,
        )

    val deleteTeamConversationUseCase: DeleteTeamConversationUseCase
        get() = DeleteTeamConversationUseCaseImpl(
            selfTeamIdProvider = selfTeamIdProvider,
            teamRepository = teamRepository,
            conversationRepository = conversationRepository,
        )

    val isSelfATeamMember: IsSelfATeamMemberUseCase get() = IsSelfATeamMemberUseCase(selfTeamIdProvider)
}
