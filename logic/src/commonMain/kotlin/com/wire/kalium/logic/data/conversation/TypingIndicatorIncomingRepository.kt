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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlin.time.ComparableTimeMark
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

internal interface TypingIndicatorIncomingRepository {
    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>>
    suspend fun clearExpiredTypingIndicators()
}

/**
 * Remembers, per conversation, when each user last sent a typing STARTED event.
 *
 * Expiry is evaluated lazily instead of running a timer per typing user: [observeUsersTyping] waits for the
 * next expiry inside the collector's coroutine. Nothing runs for conversations that are not being observed,
 * and the timer is cancelled together with the collector.
 */
internal class TypingIndicatorIncomingRepositoryImpl(
    private val userPropertyRepository: UserPropertyRepository,
    private val timeSource: TimeSource.WithComparableMarks = TimeSource.Monotonic,
) : TypingIndicatorIncomingRepository {

    // Flows are never removed, so an active collector keeps receiving updates after a clear.
    private val typingUsersByConversation =
        ConcurrentMutableMap<ConversationId, MutableStateFlow<Map<UserId, ComparableTimeMark>>>()

    private fun typingUsersFlow(conversationId: ConversationId): MutableStateFlow<Map<UserId, ComparableTimeMark>> =
        typingUsersByConversation.block { it.getOrPut(conversationId) { MutableStateFlow(emptyMap()) } }

    override suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        if (!userPropertyRepository.getTypingIndicatorStatus()) return
        val startedAt = timeSource.markNow()
        typingUsersFlow(conversationId).update { typingUsers ->
            // Prune expired entries here, so unobserved conversations don't accumulate stale users.
            typingUsers.filterValues { it.isActive() } + (userId to startedAt)
        }
    }

    override suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        typingUsersByConversation[conversationId]?.update { it - userId }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>> =
        typingUsersFlow(conversationId)
            .transformLatest { typingUsers ->
                var activeUsers = typingUsers
                while (true) {
                    activeUsers = activeUsers.filterValues { it.isActive() }
                    emit(activeUsers.keys)
                    val nextExpiry = activeUsers.values.minOfOrNull { TYPING_INDICATOR_TIMEOUT - it.elapsedNow() } ?: break
                    delay(nextExpiry)
                }
            }
            .distinctUntilChanged()

    override suspend fun clearExpiredTypingIndicators() {
        typingUsersByConversation.block { map -> map.values.forEach { it.value = emptyMap() } }
    }

    private fun ComparableTimeMark.isActive() = elapsedNow() < TYPING_INDICATOR_TIMEOUT

    companion object {
        // Matches the received typing indicator timeout in the web and iOS clients.
        val TYPING_INDICATOR_TIMEOUT = 60.seconds
    }
}
