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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

interface TypingIndicatorRepository {
    fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>>
}

class TypingIndicatorRepositoryImpl : TypingIndicatorRepository {

    private val userTypingDataSourceFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val userTypingCache = ConcurrentMutableMap<ConversationId, Set<ExpiringUserTyping>>()

    override fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        val newTypingUser = ExpiringUserTyping(userId, Clock.System.now())
        val newTypingUsers = userTypingCache[conversationId]?.toMutableSet() ?: mutableSetOf()
        newTypingUsers.add(newTypingUser)
        userTypingCache[conversationId] = newTypingUsers
        userTypingDataSourceFlow.tryEmit(Unit)
    }

    override fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        userTypingCache[conversationId] =
            userTypingCache[conversationId]?.toMutableSet()?.apply {
                this.removeAll { it.userId == userId }
            } ?: mutableSetOf()
        userTypingDataSourceFlow.tryEmit(Unit)
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>> {
        return userTypingDataSourceFlow
            .map { userTypingCache.filterUsersIdTypingInConversation(conversationId) }
            .onStart { emit(userTypingCache.filterUsersIdTypingInConversation(conversationId)) }
            .distinctUntilChanged()
    }
}

private fun ConcurrentMutableMap<ConversationId, Set<ExpiringUserTyping>>.filterUsersIdTypingInConversation(
    conversationId: ConversationId
) = this[conversationId]?.map { it.userId }?.toSet().orEmpty()

// todo expire by worker
class ExpiringUserTyping(
    val userId: UserId,
    val date: Instant
)
