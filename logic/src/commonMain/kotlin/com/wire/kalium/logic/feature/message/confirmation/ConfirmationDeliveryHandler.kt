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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Internal: Handles the send of delivery confirmation of messages.
 */
internal interface ConfirmationDeliveryHandler {
    fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: MessageId)
    suspend fun sendPendingConfirmations()
}

internal class ConfirmationDeliveryHandlerImpl(
    private val messageSender: MessageSender,
    kaliumLogger: KaliumLogger,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : ConfirmationDeliveryHandler {

    private val kaliumLogger = kaliumLogger.withTextTag("ConfirmationDeliveryHandler")
    private val pendingConfirmationMessages = ConcurrentMutableMap<ConversationId, MutableSet<String>>()
    private val holder = MutableSharedFlow<Unit>()

    override fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: String) {
        pendingConfirmationMessages.block {
            val conversationMessages = pendingConfirmationMessages[conversationId] ?: mutableSetOf()
            val isNewMessage = conversationMessages.add(messageId)
            if (isNewMessage) {
                kaliumLogger.d("Adding new message to the confirmation queue")
                pendingConfirmationMessages[conversationId] = conversationMessages
                holder.tryEmit(Unit)
            }
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun sendPendingConfirmations() {
        holder.debounce(500L)
            .distinctUntilChanged()
            .collect {
                pendingConfirmationMessages.values.forEach {
                    kaliumLogger.d("Should send all pending and clear... current queue: $it")
                }
            }
    }

}
