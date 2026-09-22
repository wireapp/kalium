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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.conversation.ReceiptModeMapperImpl
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.hasPersistedSystemMessage
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import kotlin.time.Clock

interface ReceiptModeUpdateEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationReceiptMode): Either<CoreFailure, Unit>
}

class ReceiptModeUpdateEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val messageDAO: MessageDAO,
    private val persistMessage: PersistMessageUseCase,
    private val receiptModeMapper: ReceiptModeMapper = ReceiptModeMapperImpl()
) : ReceiptModeUpdateEventHandler {

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode): Either<CoreFailure, Unit> {
        val message = Message.System(
            event.systemMessageId,
            MessageContent.ConversationReceiptModeChanged(
                receiptMode = event.receiptMode == Conversation.ReceiptMode.ENABLED
            ),
            event.conversationId,
            Clock.System.now(),
            event.senderUserId,
            Message.Status.Sent,
            Message.Visibility.VISIBLE,
            expirationData = null
        )
        return messageDAO.hasPersistedSystemMessage(message).flatMap { exists ->
            if (exists) Either.Right(Unit) else updateReceiptMode(event).flatMap { persistMessage(message) }
        }
    }

    private suspend fun updateReceiptMode(event: Event.Conversation.ConversationReceiptMode) = wrapStorageRequest {
        conversationDAO.updateConversationReceiptMode(
            event.conversationId.toDao(),
            receiptModeMapper.toDaoModel(event.receiptMode)
        )
    }

}
