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

import com.wire.kalium.common.functional.fold
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase
import io.mockative.Mockable

@Mockable
internal interface ClearConversationContentHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    )
}

internal class ClearConversationContentHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val clearLocalConversationAssets: ClearConversationAssetsLocallyUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    private val deleteRemoteSyncMessages: com.wire.kalium.logic.feature.message.sync.DeleteRemoteSyncMessagesUseCase,
) : ClearConversationContentHandler {

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    ) {
        val isSelfSender = message.senderUserId == selfUserId
        val isMessageInSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        if (isSelfSender != isMessageInSelfConversation) return

        clearConversation(messageContent.conversationId, messageContent.time)

        if (messageContent.needToRemoveLocally && isSelfSender) {
            tryToRemoveConversation(transactionContext, messageContent.conversationId)
        }
    }

    private suspend fun tryToRemoveConversation(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ) {
        conversationRepository.getConversationMembers(conversationId).fold({ false }, { it.contains(selfUserId) })
            .let { isMember ->
                if (isMember) {
                    // Sometimes MessageContent.Cleared event may come before User Left conversation event.
                    // In that case we couldn't delete it and should wait for user leave and delete after that.
                    conversationRepository.addConversationToDeleteQueue(conversationId)
                } else {
                    deleteConversation(transactionContext, conversationId)
                }
            }
    }

    private suspend fun clearConversation(conversationId: ConversationId, time: kotlinx.datetime.Instant) {
        // Delete messages from remote sync service before this timestamp
        deleteRemoteSyncMessages(conversationId, before = time.toEpochMilliseconds())

        conversationRepository.clearContent(conversationId)
        clearLocalConversationAssets(conversationId)
    }
}
