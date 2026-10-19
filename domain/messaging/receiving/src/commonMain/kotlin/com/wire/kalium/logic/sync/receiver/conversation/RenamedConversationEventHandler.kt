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

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.hasPersistedSystemMessage
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import kotlin.time.Instant

interface RenamedConversationEventHandler {
    suspend fun handle(event: Event.Conversation.RenamedConversation): Either<CoreFailure, Unit>
}

class RenamedConversationEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val messageDAO: MessageDAO,
    private val persistMessage: PersistMessageUseCase,
) : RenamedConversationEventHandler {

    override suspend fun handle(event: Event.Conversation.RenamedConversation): Either<CoreFailure, Unit> {
        val message = Message.System(
            id = event.systemMessageId,
            content = MessageContent.ConversationRenamed(event.conversationName),
            conversationId = event.conversationId,
            date = event.dateTime,
            senderUserId = event.senderUserId,
            status = Message.Status.Sent,
            expirationData = null
        )
        return messageDAO.hasPersistedSystemMessage(message).flatMap { exists ->
            if (exists) Either.Right(Unit) else {
                updateConversationName(event.conversationId, event.conversationName, event.dateTime).flatMap { persistMessage(message) }
            }
        }
    }

    private suspend fun updateConversationName(
        conversationId: ConversationId,
        conversationName: String,
        dateTime: Instant
    ) =
        wrapStorageRequest {
            conversationDAO.updateConversationName(conversationId.toDao(), conversationName, dateTime)
        }
}
