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
package com.wire.kalium.logic.feature.message.confirmation

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.feature.message.MessageSender
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Internal: Handles the send of delivery confirmation of messages.
 */
internal interface ConfirmationDeliveryHandler {
    suspend fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: MessageId)
    suspend fun sendPendingConfirmations()
}

internal class ConfirmationDeliveryHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val messageSender: MessageSender,
    kaliumLogger: KaliumLogger,
) : ConfirmationDeliveryHandler {
    private val kaliumLogger = kaliumLogger.withTextTag("ConfirmationDeliveryHandler")
    private val pendingConfirmationMessages = mutableMapOf<ConversationId, MutableSet<String>>()
    private val holder = MutableSharedFlow<Unit>()
    val mutex = Mutex()

    override suspend fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: String) = mutex.withLock {
        val conversationMessages = pendingConfirmationMessages[conversationId] ?: mutableSetOf()
        val isNewMessage = conversationMessages.add(messageId)
        if (isNewMessage) {
            kaliumLogger.d("Adding new message to the confirmation queue: $conversationId to $messageId")
            pendingConfirmationMessages[conversationId] = conversationMessages
            holder.emit(Unit)
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun sendPendingConfirmations() {
        holder.debounce(1000L)
            .collect {
                kaliumLogger.d("Collecting....")
                pendingConfirmationMessages.values.forEach {
                    kaliumLogger.d("Should send all pending and clear... current queue: $it")
                    // call conversation dao and hold types (cache) only for one to one before sending...
                }
            }
    }
}
