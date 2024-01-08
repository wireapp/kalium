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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Outgoing user typing sent events manager.
 *
 * - It will send started and stopped events.
 * - For each started sent event, will 'enqueue' a stopped event after a timeout.
 *
 */
internal interface TypingIndicatorSenderHandler {
    fun sendStoppingEvent(conversationId: ConversationId)
    fun sendStartedAndEnqueueStoppingEvent(conversationId: ConversationId)
}

internal class TypingIndicatorSenderHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    userSessionCoroutineScope: CoroutineScope
) : TypingIndicatorSenderHandler, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val outgoingStoppedQueueTypingEventsMutex = Mutex()
    private val outgoingStoppedQueueTypingEvents = mutableMapOf<ConversationId, Unit>()
    private val typingIndicatorTimeoutInSeconds = 10.toDuration(DurationUnit.SECONDS)

    /**
     * Sends a stopping event and removes it from the 'queue'.
     */
    override fun sendStoppingEvent(conversationId: ConversationId) {
        launch {
            outgoingStoppedQueueTypingEventsMutex.withLock {
                if (!outgoingStoppedQueueTypingEvents.containsKey(conversationId)) {
                    return@launch
                }
                outgoingStoppedQueueTypingEvents.remove(conversationId)
                sendTypingIndicatorStatus(conversationId, Conversation.TypingIndicatorMode.STOPPED)
            }
        }
    }

    /**
     * Sends a started event and enqueues a stopping event if sent successfully.
     */
    override fun sendStartedAndEnqueueStoppingEvent(conversationId: ConversationId) {
        launch {
            val (_, isStartedSent) = outgoingStoppedQueueTypingEventsMutex.withLock {
                if (outgoingStoppedQueueTypingEvents.containsKey(conversationId)) {
                    return@launch
                }
                val isSent = sendTypingIndicatorStatus(conversationId, Conversation.TypingIndicatorMode.STARTED)
                this to isSent
            }

            if (isStartedSent) {
                enqueueStoppedEvent(conversationId)
            }
        }
    }

    private suspend fun enqueueStoppedEvent(conversationId: ConversationId) {
        outgoingStoppedQueueTypingEvents[conversationId] = Unit
        delay(typingIndicatorTimeoutInSeconds)
        sendStoppingEvent(conversationId)
    }

    private suspend fun sendTypingIndicatorStatus(
        conversationId: ConversationId,
        typingStatus: Conversation.TypingIndicatorMode
    ): Boolean = conversationRepository.sendTypingIndicatorStatus(conversationId, typingStatus).fold({
        kaliumLogger.w("Skipping failed to send typing indicator status: $typingStatus")
        false
    }) {
        kaliumLogger.i("Successfully sent typing started indicator status: $typingStatus")
        true
    }
}
