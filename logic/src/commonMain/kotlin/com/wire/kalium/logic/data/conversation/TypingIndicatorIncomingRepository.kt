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
import com.wire.kalium.logic.util.safeComputeAndMutateSetValue
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

internal interface TypingIndicatorIncomingRepository {
    suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>>
    suspend fun clearExpiredTypingIndicators()
}

internal class TypingIndicatorIncomingRepositoryImpl(
    private val userTypingCache: ConcurrentMutableMap<ConversationId, MutableSet<UserId>>,
    private val userPropertyRepository: UserPropertyRepository
) : TypingIndicatorIncomingRepository {

    private val userTypingDataSourceFlow: MutableSharedFlow<Unit> =
        MutableSharedFlow(extraBufferCapacity = BUFFER_SIZE, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        if (userPropertyRepository.getTypingIndicatorStatus()) {
            userTypingCache.safeComputeAndMutateSetValue(conversationId) { userId }
                .also {
                    userTypingDataSourceFlow.tryEmit(Unit)
                }
        }
    }

    override suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId) {
        userTypingCache.block { entry ->
            entry[conversationId]?.apply { this.removeAll { it == userId } }
        }.also {
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>> {
        return userTypingDataSourceFlow
            .map { userTypingCache[conversationId] ?: emptySet() }
            .onStart { emit(userTypingCache[conversationId] ?: emptySet()) }
    }

    override suspend fun clearExpiredTypingIndicators() {
        userTypingCache.block { entry ->
            entry.clear()
        }.also {
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    companion object {
        const val BUFFER_SIZE = 32 // drop after this threshold
    }
}
