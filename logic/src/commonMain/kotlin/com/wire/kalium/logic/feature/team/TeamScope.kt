/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.feature.team

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.team.TeamRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.SelfTeamIdProvider
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCase
import com.wire.kalium.logic.feature.user.IsSelfATeamMemberUseCaseImpl

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

    val isSelfATeamMember: IsSelfATeamMemberUseCase get() = IsSelfATeamMemberUseCaseImpl(selfTeamIdProvider)
}
