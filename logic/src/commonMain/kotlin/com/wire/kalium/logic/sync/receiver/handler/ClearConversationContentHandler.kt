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

import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationClear
import com.wire.kalium.logic.data.conversation.conversationClear
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.conversation.ClearConversationAssetsLocallyUseCase
import com.wire.kalium.logic.feature.conversation.delete.DeleteConversationUseCase

internal interface ClearConversationContentHandler {
    suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    )
}

@Suppress("LongParameterList") // Existing account-owned cleanup dependencies and recovery callback.
internal class ClearConversationContentHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val clearLocalConversationAssets: ClearConversationAssetsLocallyUseCase,
    private val deleteConversation: DeleteConversationUseCase,
    private val recoverNSEClear: suspend (Message.Signaling, MessageContent.Cleared) -> Boolean = { _, _ -> false },
) : ClearConversationContentHandler {

    @Suppress("ReturnCount") // Keep authorization and target guards before destructive work.
    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared
    ) {
        val isSelfSender = message.senderUserId == selfUserId
        val isMessageInSelfConversation: Boolean = isMessageSentInSelfConversation(message)

        val action = conversationClear(isSelfSender, isMessageInSelfConversation, messageContent.needToRemoveLocally)
        if (action == ConversationClear.IGNORE) return
        if (messageContent.conversationId == message.conversationId) return

        val target = conversationRepository.getConversationById(messageContent.conversationId).getOrFail {
            if (it != StorageFailure.DataNotFound) throw KaliumSyncException("Conversation target lookup failed", it)
            null
        }
        if (target?.type == Conversation.Type.Self) return
        // Recovery must also work after the NSE has already removed the target conversation.
        if (recoverNSEClear(message, messageContent)) return
        if (target == null) return

        clearConversation(messageContent.conversationId)

        if (action == ConversationClear.CONVERSATION) {
            deleteConversation(transactionContext, messageContent.conversationId).getOrFail {
                throw KaliumSyncException("Conversation deletion failed", it)
            }
        }
    }

    private suspend fun clearConversation(conversationId: ConversationId) {
        clearLocalConversationAssets(conversationId).onFailure {
            kaliumLogger.w("Conversation asset cleanup failed: $it")
        }
        conversationRepository.clearContent(conversationId).getOrFail {
            throw KaliumSyncException("Conversation content cleanup failed", it)
        }
    }
}
