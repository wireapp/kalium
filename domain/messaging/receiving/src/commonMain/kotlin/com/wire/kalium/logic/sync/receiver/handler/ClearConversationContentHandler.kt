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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.receiver.conversation.ConversationLifecycleEventRepository
import com.wire.kalium.messaging.hooks.ConversationClearEventData
import com.wire.kalium.messaging.hooks.PersistenceEventHookNotifier
import com.wire.kalium.util.InternalKaliumApi

@InternalKaliumApi
public interface ClearConversationContentHandler {
    public suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared,
    )
}

@InternalKaliumApi
public fun interface ClearConversationAssetsLocally {
    public suspend operator fun invoke(conversationId: ConversationId): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public fun interface WholeConversationDeletion {
    public suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public class ClearConversationContentHandlerImpl public constructor(
    private val conversationLifecycleEventRepository: ConversationLifecycleEventRepository,
    private val selfUserId: UserId,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase,
    private val clearLocalConversationAssets: ClearConversationAssetsLocally,
    private val deleteConversation: WholeConversationDeletion,
    private val persistenceEventHookNotifier: PersistenceEventHookNotifier,
) : ClearConversationContentHandler {

    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        message: Message.Signaling,
        messageContent: MessageContent.Cleared,
    ) {
        val isSelfSender = message.senderUserId == selfUserId
        val isMessageInSelfConversation = isMessageSentInSelfConversation(message)

        if (isSelfSender != isMessageInSelfConversation) return

        clearConversation(messageContent.conversationId)

        if (messageContent.needToRemoveLocally && isSelfSender) {
            deleteConversation(transactionContext, messageContent.conversationId)
        }
    }

    private suspend fun clearConversation(conversationId: ConversationId) {
        conversationLifecycleEventRepository.clearContent(conversationId)
        clearLocalConversationAssets(conversationId)
        persistenceEventHookNotifier.onConversationCleared(
            ConversationClearEventData(conversationId),
            selfUserId,
        )
    }
}
