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
package com.wire.kalium.persistence.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * In-memory cache for sharing flows.
 *
 * New collectors will get the latest value immediately.
 * It converts produced flows into shared flows with a replay cache of 1.
 *
 * Each individual call to [get] will get its own buffer (size of 1),
 * and oldest values are dropped if the collector is slow.
 *
 * Once the cached flows have no more collectors, the flows are removed from memory after [flowTimeoutDuration].
 *
 * Once the [cacheScope] is canceled, the whole cache stops.
 */
class FlowCache<Key : Any, Value>(
    private val cacheScope: CoroutineScope,
    private val flowTimeoutDuration: Duration = FLOW_OBSERVING_TIMEOUT_IN_MILLIS.milliseconds,
) {

    private val mutex = Mutex()
    private val storage = hashMapOf<Key, Flow<Value>>()

    suspend fun get(
        key: Key,
        flowProducer: suspend (key: Key) -> Flow<Value>
    ): Flow<Value> {
        suspend fun createFlow() = flowProducer(key)
            .onCompletion {
                remove(key)
            }
            .shareIn(
                scope = cacheScope,
                started = SharingStarted.WhileSubscribed(
                    stopTimeoutMillis = flowTimeoutDuration.inWholeMilliseconds
                ),
                replay = 1
            )

        return mutex.withLock {
            val result = storage.getOrPut(key) {
                createFlow()
            }
            result
        }.distinctUntilChanged()
            .buffer(
                capacity = 1,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
    }

    suspend fun remove(key: Key) = mutex.withLock {
        storage.remove(key)
    }

    companion object {
        const val FLOW_OBSERVING_TIMEOUT_IN_MILLIS = 5_000L
    }
}
