/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Incoming user typing received events, cleanup manager
 * todo, replicate same as outgoing
 */
internal class TypingIndicatorIncomingEventManager(
    userSessionCoroutineScope: CoroutineScope,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val incomingStoppedQueueTypingEventsMutex = Mutex()
    private val incomingStoppedQueueTypingEvents = mutableMapOf<ConversationId, MutableSet<ExpiringUserTyping>>()
    private val typingIndicatorTimeoutInSeconds = 30.toDuration(DurationUnit.SECONDS)

    private val incomingTypingUserDataSourceFlow: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = TypingIndicatorRepositoryImpl.BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        incomingStoppedQueueTypingEventsMutex.withLock {
            val values =
                if (incomingStoppedQueueTypingEvents.containsKey(conversationId)) incomingStoppedQueueTypingEvents[conversationId]!! else mutableSetOf()

            values.add(ExpiringUserTyping(userId, Clock.System.now()))
            incomingStoppedQueueTypingEvents[conversationId] = values.also {
                incomingTypingUserDataSourceFlow.tryEmit(Unit)
            }

            delay(typingIndicatorTimeoutInSeconds)
            removeTypingUserInConversation(conversationId, userId)
        }
    }

    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        incomingStoppedQueueTypingEventsMutex.withLock {
            incomingStoppedQueueTypingEvents.also { entry ->
                entry[conversationId]?.apply { this.removeAll { it.userId == userId } }
            }.also {
                incomingTypingUserDataSourceFlow.tryEmit(Unit)
            }
        }
    }

    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>> {
        return incomingTypingUserDataSourceFlow
            .map { incomingStoppedQueueTypingEvents[conversationId] ?: emptySet() }
            .onStart { emit(incomingStoppedQueueTypingEvents[conversationId] ?: emptySet()) }
    }
}
