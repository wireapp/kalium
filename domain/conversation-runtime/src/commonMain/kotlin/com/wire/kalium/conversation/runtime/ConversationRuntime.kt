@file:OptIn(com.wire.kalium.conversation.ExperimentalConversationApi::class)

/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.conversation.runtime

import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextFailure
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.conversation.ConversationProtocolStateResult
import com.wire.kalium.conversation.ConversationProtocolStateStore
import com.wire.kalium.conversation.ExperimentalConversationApi
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** Runtime-local bounded context cache; it never mixes identities or persists a catalog. */
@ExperimentalConversationApi
public class ConversationRuntime(
    private val provider: ConversationContextProvider,
    private val maxCachedContexts: Int = DEFAULT_MAX_CACHED_CONTEXTS,
    private val maxCacheAge: Duration = DEFAULT_MAX_CACHE_AGE,
) : ConversationContextProvider {
    private val mutex = Mutex()
    private val cache = mutableMapOf<ConversationId, CachedContext>()

    init {
        require(maxCachedContexts > 0) { "maxCachedContexts must be positive" }
        require(maxCacheAge.isPositive()) { "maxCacheAge must be positive" }
    }

    override suspend fun getForCall(conversationId: ConversationId): ConversationContextResult {
        mutex.withLock {
            val cached = cache[conversationId]
            if (cached != null && !cached.createdAt.plus(maxCacheAge).hasPassedNow()) {
                cached.context
            } else {
                cache.remove(conversationId)
                null
            }
        }?.let { return ConversationContextResult.Success(it) }
        return provider.getForCall(conversationId).also { result ->
            if (result is ConversationContextResult.Success) {
                mutex.withLock {
                    if (cache.size >= maxCachedContexts) cache.remove(cache.keys.first())
                    cache[conversationId] = CachedContext(result.context, TimeSource.Monotonic.markNow())
                }
            }
        }
    }

    public suspend fun invalidate(conversationId: ConversationId): Unit = mutex.withLock {
        cache.remove(conversationId)
    }

    public suspend fun clear(): Unit = mutex.withLock { cache.clear() }

    private companion object {
        const val DEFAULT_MAX_CACHED_CONTEXTS = 32
        val DEFAULT_MAX_CACHE_AGE: Duration = 30.seconds
    }

    private data class CachedContext(val context: CallConversationContext, val createdAt: TimeMark)
}

/** Records the minimal protocol/group mapping whenever remote or local context resolution succeeds. */
@ExperimentalConversationApi
public class ProtocolRecordingConversationContextProvider(
    private val delegate: ConversationContextProvider,
    private val protocolStateStore: ConversationProtocolStateStore,
) : ConversationContextProvider {
    override suspend fun getForCall(conversationId: ConversationId): ConversationContextResult =
        when (val result = delegate.getForCall(conversationId)) {
            is ConversationContextResult.Failure -> result
            is ConversationContextResult.Success -> when (
                val saved = protocolStateStore.save(conversationId, result.context.protocol)
            ) {
                is ConversationProtocolStateResult.Failure -> ConversationContextResult.Failure(
                    ConversationContextFailure.Local(saved.description, saved.cause),
                )
                is ConversationProtocolStateResult.Success -> result
            }
        }
}
