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
package com.wire.kalium.logic.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * A buffer that will collect items and emit list of all items buffered since last emitted list
 * only when it has reached size of [capacity] or when [timeout] has passed since last item added.
 */
@OptIn(FlowPreview::class)
internal class DebounceBuffer<T>(private val capacity: Int, private val timeout: Duration, scope: CoroutineScope) {

    private val buffer = mutableListOf<T>()
    private val mutex = Mutex()
    private val trigger = MutableSharedFlow<Long>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val sharedFlow = trigger
        .debounce { it }
        .map { getAllAndClear() }
        .shareIn(scope, SharingStarted.Eagerly, 1)
    suspend fun add(value: T) = mutex.withLock {
        if (!buffer.contains(value)) {
            buffer.add(value)
            trigger.emit(
                if (buffer.size >= capacity) 0.seconds.inWholeMilliseconds
                else timeout.inWholeMilliseconds
            )
        }
    }

    private suspend fun getAllAndClear(): List<T> = mutex.withLock {
        buffer.toList().also { buffer.clear() }
    }

    fun observe(): Flow<List<T>> = sharedFlow.filter { it.isNotEmpty() }
}
