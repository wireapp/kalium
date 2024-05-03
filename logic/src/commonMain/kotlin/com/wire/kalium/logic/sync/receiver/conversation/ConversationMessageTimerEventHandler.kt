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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.persistence.dao.conversation.ConversationDAO

interface ConversationMessageTimerEventHandler {
    suspend fun handle(event: Event.Conversation.ConversationMessageTimer): Either<CoreFailure, Unit>
}

internal class ConversationMessageTimerEventHandlerImpl(
    private val conversationDAO: ConversationDAO,
    private val persistMessage: PersistMessageUseCase,
) : ConversationMessageTimerEventHandler {

    override suspend fun handle(event: Event.Conversation.ConversationMessageTimer): Either<CoreFailure, Unit> =
        updateMessageTimer(event)
            .onSuccess {
                val message = Message.System(
                    event.id,
                    MessageContent.ConversationMessageTimerChanged(
                        messageTimer = event.messageTimer
                    ),
                    event.conversationId,
                    event.timestampIso,
                    event.senderUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null
                )

                persistMessage(message)
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
            .onFailure { coreFailure ->
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.FAILURE,
                        event,
                        Pair("errorInfo", "$coreFailure")
                    )
            }

    private suspend fun updateMessageTimer(event: Event.Conversation.ConversationMessageTimer) = wrapStorageRequest {
        conversationDAO.updateMessageTimer(
            event.conversationId.toDao(),
            event.messageTimer
        )
    }
}
