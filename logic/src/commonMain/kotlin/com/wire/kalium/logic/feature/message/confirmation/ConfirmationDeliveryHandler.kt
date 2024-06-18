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

import com.benasher44.uuid.uuid4
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.util.DateTimeUtil
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.onEach
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
    private val syncManager: SyncManager,
    private val selfUserId: UserId,
    private val currentClientIdProvider: CurrentClientIdProvider,
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
        holder.debounce(500L)
            .onEach {
                syncManager.waitUntilLive()
            }.collect {
                kaliumLogger.d("Collecting....")
                with(pendingConfirmationMessages.iterator()) {
                    forEach { (conversationId, messages) ->
                        conversationRepository.observeCacheDetailsById(conversationId).flatMap { conversation: Flow<Conversation?> ->
                            conversation.filter { it?.type == Conversation.Type.ONE_ON_ONE }
                                .firstOrNull()?.let {
                                    sendDeliveredSignal(it, messages.toList()).fold({ error ->
                                        kaliumLogger.e("Error on sending delivered signal $error for $conversationId")
                                    }, {
                                        kaliumLogger.d("Delivered confirmation sent for $conversation and message count: ${messages.size}")
                                        remove()
                                        kaliumLogger.d("Current queue ${pendingConfirmationMessages.entries}")

                                    })
                                } ?: remove()
                            Unit.right()
                        }
                    }
                }
            }
    }

    private suspend fun sendDeliveredSignal(conversation: Conversation, messages: List<MessageId>) =
        currentClientIdProvider().flatMap { currentClientId ->
            val message = Message.Signaling(
                id = uuid4().toString(),
                content = MessageContent.Receipt(ReceiptType.DELIVERED, messages),
                conversationId = conversation.id,
                date = DateTimeUtil.currentIsoDateTimeString(),
                senderUserId = selfUserId,
                senderClientId = currentClientId,
                status = Message.Status.Pending,
                isSelfMessage = true,
                expirationData = null
            )
            messageSender.sendMessage(message)
        }
}
