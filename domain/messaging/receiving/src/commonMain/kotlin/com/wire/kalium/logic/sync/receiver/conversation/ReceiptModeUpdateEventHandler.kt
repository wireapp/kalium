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

import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.util.createEventProcessingLogger
import kotlinx.datetime.Clock
import kotlin.uuid.Uuid

public fun interface ReceiptModeUpdateEventHandler {
    public suspend fun handle(event: Event.Conversation.ConversationReceiptMode)
}

public class ReceiptModeUpdateEventHandlerImpl public constructor(
    private val conversationEventRepository: ConversationEventRepository,
    private val persistMessage: PersistMessageUseCase,
) : ReceiptModeUpdateEventHandler {

    override suspend fun handle(event: Event.Conversation.ConversationReceiptMode) {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        conversationEventRepository.updateReceiptMode(event.conversationId, event.receiptMode)
            .onSuccess {
                val message = Message.System(
                    Uuid.random().toString(),
                    MessageContent.ConversationReceiptModeChanged(
                        receiptMode = event.receiptMode == Conversation.ReceiptMode.ENABLED,
                    ),
                    event.conversationId,
                    Clock.System.now(),
                    event.senderUserId,
                    Message.Status.Sent,
                    Message.Visibility.VISIBLE,
                    expirationData = null,
                )

                persistMessage(message)
                eventLogger.logSuccess()
            }
            .onFailure { eventLogger.logFailure(it) }
    }
}
