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

package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.common.functional.fold
import io.mockative.Mockable

@Mockable
internal interface ClearConversationContentHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    )
}

internal class ClearConversationContentHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val clearLocalConversationAssets: ClearConversationAssetsLocallyUseCase
) : ClearConversationContentHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    ) {
        val isSelfSender = message.senderUserId == selfUserId
        val isMessageInSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isSelfSender != isMessageInSelfConversation) return

        clearConversation(messageContent.conversationId)

        if (messageContent.needToRemoveLocally && isSelfSender) {
            tryToRemoveConversation(messageContent.conversationId)
        }
    }

    private suspend fun tryToRemoveConversation(conversationId: ConversationId) {
        conversationRepository.getConversationMembers(conversationId).fold({ false }, { it.contains(selfUserId) })
            .let { isMember ->
                if (isMember) {
                    // Sometimes MessageContent.Cleared event may come before User Left conversation event.
                    // In that case we couldn't delete it and should wait for user leave and delete after that.
                    conversationRepository.addConversationToDeleteQueue(conversationId)
                } else {
                    conversationRepository.deleteConversation(conversationId)
                }
            }
    }

    private suspend fun clearConversation(conversationId: ConversationId) {
        conversationRepository.clearContent(conversationId)
        clearLocalConversationAssets(conversationId)
    }
}
