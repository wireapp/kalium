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
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
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

    private val mutex = Mutex()
    private val typingConversations = mutableSetOf<ConversationId>()
    private val typingIndicatorTimeoutInSeconds = 10.toDuration(DurationUnit.SECONDS)

    /**
     * Removes the conversation from the typing set and sends a stopping event.
     */
    override fun sendStoppingEvent(conversationId: ConversationId) {
        launch {
            if (!mutex.withLock { typingConversations.remove(conversationId) }) return@launch
            sendTypingIndicatorStatus(conversationId, Conversation.TypingIndicatorMode.STOPPED)
        }
    }

    /**
     * Sends a started event and suppresses further starts until the timeout.
     */
    override fun sendStartedAndEnqueueStoppingEvent(conversationId: ConversationId) {
        launch {
            if (!mutex.withLock { typingConversations.add(conversationId) }) return@launch
            val isStartedSent = sendTypingIndicatorStatus(conversationId, Conversation.TypingIndicatorMode.STARTED)
            delay(typingIndicatorTimeoutInSeconds)
            if (isStartedSent) {
                sendStoppingEvent(conversationId)
            } else {
                mutex.withLock { typingConversations.remove(conversationId) }
            }
        }
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
