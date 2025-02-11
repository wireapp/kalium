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
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.MessageId
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.logStructuredJson
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first

/**
 * Internal: Handles the send of delivery confirmation of messages.
 */
internal interface ConfirmationDeliveryHandler {
    suspend fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: MessageId)
    suspend fun sendPendingConfirmations()
}

internal class ConfirmationDeliveryHandlerImpl(
    private val syncManager: SyncManager,
    private val conversationRepository: ConversationRepository,
    private val sendDeliverSignalUseCase: SendDeliverSignalUseCase,
    private val pendingConfirmationMessages: ConcurrentMutableMap<ConversationId, MutableSet<String>> =
        ConcurrentMutableMap(),
    kaliumLogger: KaliumLogger
) : ConfirmationDeliveryHandler {

    private val kaliumLogger = kaliumLogger.withTextTag("ConfirmationDeliveryHandler")
    private val holder = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun enqueueConfirmationDelivery(conversationId: ConversationId, messageId: String) {
        val conversationMessages = pendingConfirmationMessages.computeIfAbsent(conversationId) { mutableSetOf() }
        val isNewMessage = conversationMessages.add(messageId)
        if (isNewMessage) {
            kaliumLogger.logStructuredJson(
                level = KaliumLogLevel.DEBUG,
                leadingMessage = "Adding new message to the confirmation queue: ${messageId.obfuscateId()}",
                jsonStringKeyValues = mapOf(
                    "conversationId" to conversationId.toLogString(),
                    "message" to messageId.obfuscateId(),
                    "queueCount" to pendingConfirmationMessages.size
                )
            )
            holder.emit(Unit)
        }
    }

    @OptIn(FlowPreview::class)
    override suspend fun sendPendingConfirmations() {
        holder.debounce(DEBOUNCE_SEND_CONFIRMATION_TIME).collectLatest {
            syncManager.waitUntilLive()
            kaliumLogger.d("Started collecting pending messages for delivery confirmation")
            val messagesToSend = pendingConfirmationMessages.block { it.toMap() }
            messagesToSend.forEach { (conversationId, messages) ->
                conversationRepository.observeConversationById(conversationId).first().flatMap { conversation ->
                    if (conversation.type == Conversation.Type.ONE_ON_ONE) {
                        sendDeliverSignalUseCase(
                            conversation = conversation,
                            messages = messages.toList()
                        ).onSuccess {
                            pendingConfirmationMessages.block {
                                val currentMessages = it[conversationId]
                                if (currentMessages != null) {
                                    currentMessages.removeAll(messages.toSet())
                                    if (currentMessages.isEmpty()) {
                                        it.remove(conversationId)
                                    }
                                }
                            }
                        }
                    } else {
                        kaliumLogger.logStructuredJson(
                            level = KaliumLogLevel.DEBUG,
                            leadingMessage = "Skipping group conversation: ${conversation.id.toLogString()}",
                            jsonStringKeyValues = mapOf(
                                "conversationId" to conversation.id.toLogString(),
                                "messageCount" to messages.size
                            )
                        )
                    }
                    Unit.right()
                }
            }
            kaliumLogger.d("Finished collecting pending messages for delivery confirmation")
        }
    }

    private companion object {
        const val DEBOUNCE_SEND_CONFIRMATION_TIME = 1_000L
    }
}
