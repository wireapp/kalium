@file:OptIn(com.wire.kalium.conversation.ExperimentalConversationApi::class)

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.CallConversationType
import com.wire.kalium.conversation.CallMember
import com.wire.kalium.conversation.ConversationContextFailure
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.conversation.local.LocalConversationContextDataSource
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.flow.first

/** Maps the existing full-client repository into the operational calling context. */
internal class ClientConversationContextDataSource(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
) : LocalConversationContextDataSource {
    @Suppress("ReturnCount")
    override suspend fun load(conversationId: ConversationId): ConversationContextResult {
        val conversation = conversationRepository.getConversationById(conversationId).getOrNull()
            ?: return localFailure("Conversation is not available in client storage")
        val recipients = conversationRepository.getConversationRecipientsForCalling(conversationId).getOrNull()
            ?: return localFailure("Conversation clients could not be resolved")
        val members = conversationRepository.observeConversationMembers(conversationId).first()
        return ConversationContextResult.Success(
            CallConversationContext(
                conversationId = conversation.id,
                type = conversation.type.toCallType(),
                protocol = conversation.protocol.toCallProtocol(),
                members = members.map { member ->
                    CallMember(
                        userId = member.id,
                        role = member.role.toString(),
                        isSelf = member.id == selfUserId,
                        isService = false,
                    )
                },
                clients = recipients.flatMap { recipient ->
                    recipient.clients.map { clientId -> CallClient(recipient.id, clientId.value) }
                },
                teamId = conversation.teamId?.value,
            ),
        )
    }

    private fun Conversation.Type.toCallType(): CallConversationType = when (this) {
        Conversation.Type.Self -> CallConversationType.SELF
        Conversation.Type.OneOnOne -> CallConversationType.ONE_TO_ONE
        Conversation.Type.ConnectionPending -> CallConversationType.CONNECTION_PENDING
        Conversation.Type.Group.Regular -> CallConversationType.GROUP
        Conversation.Type.Group.Channel -> CallConversationType.CHANNEL
        Conversation.Type.Group.Meeting -> CallConversationType.MEETING
    }

    private fun Conversation.ProtocolInfo.toCallProtocol(): CallConversationProtocol = when (this) {
        Conversation.ProtocolInfo.Proteus -> CallConversationProtocol.Proteus
        is Conversation.ProtocolInfo.MLS -> CallConversationProtocol.Mls(groupId, epoch)
        is Conversation.ProtocolInfo.Mixed -> CallConversationProtocol.Mixed(groupId, epoch)
    }

    private fun localFailure(description: String): ConversationContextResult.Failure =
        ConversationContextResult.Failure(ConversationContextFailure.Local(description))
}
