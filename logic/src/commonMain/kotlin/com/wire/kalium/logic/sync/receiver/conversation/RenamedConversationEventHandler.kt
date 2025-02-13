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

import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import kotlinx.datetime.Instant

interface RenamedConversationEventHandler {
    suspend fun handle(event: Event.Conversation.RenamedConversation)
}

internal class RenamedConversationEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val persistMessage: PersistMessageUseCase,
) : RenamedConversationEventHandler {

    override suspend fun handle(event: Event.Conversation.RenamedConversation) {
        val logger = kaliumLogger.createEventProcessingLogger(event)
        updateConversationName(event.conversationId, event.conversationName, event.dateTime)
            .onSuccess {
                val message = Message.System(
                    id = event.id,
                    content = MessageContent.ConversationRenamed(event.conversationName),
                    conversationId = event.conversationId,
                    date = event.dateTime,
                    senderUserId = event.senderUserId,
                    status = Message.Status.Sent,
                    expirationData = null
                )
                persistMessage(message)
                logger.logSuccess()
            }
            .onFailure(logger::logFailure)
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
