/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId

/**
 * Checks if the current user should be muted remotely.
 * More details in the use case:
 * https://wearezeta.atlassian.net/wiki/spaces/ENGINEERIN/pages/969605169/Use+case+conversation+admin+mutes+a+remote+participant
 */
internal interface ShouldRemoteMuteChecker {
    fun check(
        senderUserId: UserId,
        selfUserId: UserId,
        selfClientId: String,
        targets: MessageContent.Calling.Targets?,
        conversationMembers: List<Conversation.Member>
    ): Boolean
}

internal class ShouldRemoteMuteCheckerImpl : ShouldRemoteMuteChecker {
    override fun check(
        senderUserId: UserId,
        selfUserId: UserId,
        selfClientId: String,
        targets: MessageContent.Calling.Targets?,
        conversationMembers: List<Conversation.Member>
    ): Boolean = isSenderAnAdmin(senderUserId, conversationMembers) && isCurrentClientTargeted(selfUserId, selfClientId, targets)

    // Only admins can remotely mute other participants, so we need to check if the sender is an admin or not.
    private fun isSenderAnAdmin(senderUserId: UserId, conversationMembers: List<Conversation.Member>): Boolean =
        conversationMembers.any { member ->
            member.id == senderUserId && member.role == Conversation.Member.Role.Admin
        }

    // Because of the nature of MLS (where all the MLS group members always receive a message),
    // we need to check if the current client is a target of the remote mute or not.
    private fun isCurrentClientTargeted(selfUserId: UserId, selfClientId: String, targets: MessageContent.Calling.Targets?): Boolean =
        targets?.domainToUserIdToClients?.values.orEmpty().any { userClientsMap ->
            userClientsMap[selfUserId.value].orEmpty().any { client ->
                client == selfClientId
            }
        }
}
