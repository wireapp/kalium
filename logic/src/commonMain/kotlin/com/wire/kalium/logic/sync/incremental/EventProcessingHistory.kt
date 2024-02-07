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

package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logic.data.event.Event
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory event storage, used to check if an event was already
 * dealt with and avoid duplication. _i.e._ present in both pending and live sources.
 *
 * All operations are thread-safe.
 */
internal class EventProcessingHistory {
    private val events = hashSetOf<Event>()
    private var lastAddedEvent: Event? = null
    private val mutex = Mutex()

    /**
     * Adds an [event] to the end of this storage.
     */
    suspend fun add(event: Event) = mutex.withLock {
        lastAddedEvent = event
        events.add(event)
    }

    /**
     * @return True if the [event] is present in this storage. False otherwise
     */
    suspend fun contains(event: Event): Boolean = mutex.withLock {
        events.contains(event)
    }

    /**
     * Removes the [event] from this storage if present.
     * @return True if [event] was in this storage and was removed. False otherwise.
     */
    suspend fun remove(event: Event) = mutex.withLock {
        events.remove(event)
    }

    /**
     * Clears the whole storage if this [event] is the one added most recently.
     * @return True if this [event] was the one added most recently and the storage was cleared.
     *         False otherwise.
     */
    suspend fun clearHistoryIfLastEventEquals(event: Event): Boolean = mutex.withLock {
        if (event == lastAddedEvent) {
            events.clear()
            lastAddedEvent = null
            true
        } else {
            false
        }
    }

    /**
     * Clears the storage, removes every previously added [Event].
     */
    suspend fun clear() = mutex.withLock {
        lastAddedEvent = null
        events.clear()
    }
}
