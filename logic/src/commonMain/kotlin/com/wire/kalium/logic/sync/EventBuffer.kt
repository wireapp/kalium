package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.event.Event
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class EventBuffer {
    private val events = mutableListOf<Event>()
    private val mutex = Mutex()

    suspend fun addToBuffer(event: Event) = mutex.withLock {
        events.add(event)
    }

    suspend fun isEventPresentInBuffer(event: Event): Boolean = mutex.withLock {
        events.contains(event)
    }

    suspend fun removeEventFromBuffer(event: Event) = mutex.withLock {
        events.remove(event)
    }

    suspend fun clearBufferIfLastEventEquals(event: Event): Boolean = mutex.withLock {
        if (events.last() == event) {
            events.clear()
            true
        } else {
            false
        }
    }

    suspend fun clearBuffer() = mutex.withLock {
        events.clear()
    }
}
