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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

internal interface TypingIndicatorIncomingRepository {
    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>>
    suspend fun clearExpiredTypingIndicators()
}

internal class TypingIndicatorIncomingRepositoryImpl(
    private val userTypingCache: ConcurrentMutableMap<ConversationId, MutableSet<UserId>>,
    private val userPropertyRepository: UserPropertyRepository,
    private val userSessionCoroutineScope: CoroutineScope
) : TypingIndicatorIncomingRepository {

    // Accessed only while holding userTypingCache's lock.
    private val expiryJobs = mutableMapOf<Pair<ConversationId, UserId>, Job>()

    private val userTypingDataSourceFlow: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            userTypingCache.block { entry ->
                val key = conversationId to userId
                expiryJobs.remove(key)?.cancel()
                entry.getOrPut(conversationId) { mutableSetOf() }.add(userId)
                val expiryJob = createExpiryJob(conversationId, userId)
                expiryJobs[key] = expiryJob
                expiryJob.start()
                userTypingDataSourceFlow.tryEmit(Unit)
            }
        }
    }

    private fun createExpiryJob(conversationId: ConversationId, userId: UserId): Job =
        userSessionCoroutineScope.launch(start = CoroutineStart.LAZY) {
            delay(TYPING_INDICATOR_TIMEOUT)
            userTypingCache.block { cache ->
                val key = conversationId to userId
                // A refreshed STARTED may have replaced this timer before it acquired the lock.
                if (expiryJobs[key] == coroutineContext[Job]) {
                    expiryJobs.remove(key)
                    cache[conversationId]?.remove(userId)
                    userTypingDataSourceFlow.tryEmit(Unit)
                }
            }
        }

    override suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        userTypingCache.block { entry ->
            expiryJobs.remove(conversationId to userId)?.cancel()
            entry[conversationId]?.remove(userId)
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>> {
        return userTypingDataSourceFlow
            .map { typingUsersSnapshot(conversationId) }
            .onStart { emit(typingUsersSnapshot(conversationId)) }
    }

    private fun typingUsersSnapshot(conversationId: ConversationId): Set<UserId> =
        userTypingCache.block { it[conversationId]?.toSet() ?: emptySet() }

    override suspend fun clearExpiredTypingIndicators() {
        userTypingCache.block { entry ->
            expiryJobs.values.forEach { it.cancel() }
            expiryJobs.clear()
            entry.clear()
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold

        // Match the received typing indicator timeout in the web and iOS clients.
        val TYPING_INDICATOR_TIMEOUT = 60.seconds
    }
}
