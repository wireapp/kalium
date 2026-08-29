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
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/** Current typing-indicator preference required by incoming typing events. */
@InternalKaliumApi
public fun interface TypingIndicatorStatusProvider {
    public suspend fun getTypingIndicatorStatus(): Boolean
}

@InternalKaliumApi
public interface TypingIndicatorIncomingRepository {
    public suspend fun addTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    public suspend fun removeTypingUserInConversation(conversationId: ConversationId, userId: UserId)
    public suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>>
    public suspend fun clearExpiredTypingIndicators()
}

/** Shared incoming-typing cache used by the app and future bounded receivers. */
@InternalKaliumApi
public class TypingIndicatorIncomingRepositoryImpl public constructor(
    private val userPropertyRepository: TypingIndicatorStatusProvider,
) : TypingIndicatorIncomingRepository {

    private val userTypingCache: ConcurrentMutableMap<ConversationId, MutableSet<UserId>> = ConcurrentMutableMap()
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

    override suspend fun observeUsersTyping(conversationId: ConversationId): Flow<Set<UserId>> =
        userTypingDataSourceFlow
            .map { userTypingCache[conversationId] ?: emptySet() }
            .onStart { emit(userTypingCache[conversationId] ?: emptySet()) }

    override suspend fun clearExpiredTypingIndicators() {
        userTypingCache.block { entry ->
            entry.clear()
        }.also {
            userTypingDataSourceFlow.tryEmit(Unit)
        }
    }

    public companion object {
        public const val BUFFER_SIZE: Int = 32
    }
}

private fun <K, V> ConcurrentMutableMap<K, MutableSet<V>>.safeComputeAndMutateSetValue(
    key: K,
    value: () -> V,
): MutableSet<V> = block {
    val values = if (containsKey(key)) get(key)!! else mutableSetOf()
    values.add(value())
    set(key, values)
    values
}
