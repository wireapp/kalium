/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.channel

import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase

/**
 * Use case to check if the self user is eligible to add participants to a channel.
 */
interface IsSelfEligibleToAddParticipantsToChannelUseCase {
    suspend operator fun invoke(conversationId: ConversationId): Boolean
}

internal class IsSelfEligibleToAddParticipantsToChannelUseCaseImpl(
    val selfUser: GetSelfUserUseCase,
    val conversationRepository: ConversationRepository
) : IsSelfEligibleToAddParticipantsToChannelUseCase {
    override suspend operator fun invoke(conversationId: ConversationId): Boolean =
        conversationRepository.getChannelAddPermission(conversationId).fold(
            { false },
            {
                val eligibleUserTypes: Set<UserType> = when (it) {
                    ConversationDetails.Group.Channel.ChannelAddPermission.ADMINS -> setOf(UserType.ADMIN, UserType.OWNER)
                    ConversationDetails.Group.Channel.ChannelAddPermission.EVERYONE -> setOf(
                        UserType.ADMIN,
                        UserType.OWNER,
                        UserType.INTERNAL
                    )
                }

                selfUser()?.userType in eligibleUserTypes
            }
        )
}
