package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Stores pending events as they are collected, used to
 * check if an event was already collected (duplicated, present in both pending and live sources).
 *
 * All operations are thread-safe.
 */
internal class PendingEventsBuffer {
    private val events = mutableListOf<Event>()
    private val mutex = Mutex()

    /**
     * Adds an [event] to the end of this storage.
     */
    suspend fun addToBuffer(event: Event) = mutex.withLock {
        events.add(event)
    }

    /**
     * @return True if the [event] is present in this storage. False otherwise
     */
    suspend fun isEventPresentInBuffer(event: Event): Boolean = mutex.withLock {
        events.contains(event)
    }

    /**
     * Removes the [event] from this storage if present.
     * @return True if [event] was in this storage and was removed. False otherwise.
     */
    suspend fun removeEventFromBuffer(event: Event) = mutex.withLock {
        events.remove(event)
    }

    /**
     * Clears the whole storage if this [event] is the one added most recently.
     * @return True if this [event] was the one added most recently and the storage was cleared.
     *         False otherwise.
     */
    suspend fun clearBufferIfLastEventEquals(event: Event): Boolean = mutex.withLock {
        if (events.last() == event) {
            events.clear()
            true
        } else {
            false
        }
    }

    /**
     * Clears the storage, removes every previously added [Event].
     */
    suspend fun clearBuffer() = mutex.withLock {
        events.clear()
    }
}
