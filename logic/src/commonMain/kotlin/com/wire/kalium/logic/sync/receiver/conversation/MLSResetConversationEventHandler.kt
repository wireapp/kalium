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
package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import io.mockative.Mockable

@Mockable
interface MLSResetConversationEventHandler {
    suspend fun handle(transaction: CryptoTransactionContext, event: Event.Conversation.MLSReset)
}

internal class MLSResetConversationEventHandlerImpl(
    private val selfUserId: UserId,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchConversation: FetchConversationUseCase,
) : MLSResetConversationEventHandler {
    override suspend fun handle(transaction: CryptoTransactionContext, event: Event.Conversation.MLSReset) {
        val isFromOtherUser = event.from != selfUserId

        // If the event is from same user do reset only if local group id does not match new group id.
        if (isFromOtherUser || event.newGroupID != getCurrentGroupId(event.conversationId)) {
            transaction.mls?.let { mlsContext ->
                mlsConversationRepository.leaveGroup(mlsContext, event.groupID)

                // Will be replaced by updating Group ID when it is added in a new
                // version of mls-reset event.
                fetchConversation(transaction, event.conversationId)
            }
        }
    }

    private suspend fun getCurrentGroupId(conversationId: ConversationId) =
        conversationRepository.getConversationById(conversationId).getOrNull()?.mlsProtocolInfo()?.groupId

    private fun Conversation.mlsProtocolInfo(): Conversation.ProtocolInfo.MLS? {
        return when (this.protocol) {
            is Conversation.ProtocolInfo.MLS -> this.protocol as? Conversation.ProtocolInfo.MLS
            else -> null
        }
    }
}
