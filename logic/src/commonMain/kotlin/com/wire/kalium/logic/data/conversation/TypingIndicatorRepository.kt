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

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.util.safeComputeAndMutateSetValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

internal interface TypingIndicatorRepository {
    fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>>
}

internal class TypingIndicatorRepositoryImpl(
    private val userTypingCache: ConcurrentMutableMap<ConversationId, MutableSet<ExpiringUserTyping>>
) : TypingIndicatorRepository {

    private val userTypingDataSourceFlow: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        userTypingCache.safeComputeAndMutateSetValue(conversationId) { ExpiringUserTyping(userId, Clock.System.now()) }
            .also {
                userTypingDataSourceFlow.tryEmit(Unit)
            }
    }

    override fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        userTypingCache.block { entry ->
            entry[conversationId]?.apply { this.removeAll { it.userId == userId } }
        }.also {
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<ExpiringUserTyping>> {
        return userTypingDataSourceFlow
            .map { userTypingCache[conversationId] ?: emptySet() }
            .onStart { emit(userTypingCache[conversationId] ?: emptySet()) }
    }

    companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
    }
}

// todo expire by worker
data class ExpiringUserTyping(
    val userId: UserId,
    val date: Instant
) {
    override fun equals(other: Any?): Boolean {
        return other != null && when (other) {
            is ExpiringUserTyping -> other.userId == this.userId
            else -> false
        }
    }

    override fun hashCode(): Int {
        return this.userId.hashCode()
    }
}
