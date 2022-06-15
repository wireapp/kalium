package com.wire.kalium.logic.sync

import com.wire.kalium.logic.framework.TestEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PendingEventsBufferTest {

    private lateinit var eventsBuffer: PendingEventsBuffer

    @BeforeTest
    fun setup() {
        eventsBuffer = PendingEventsBuffer()
    }

    @Test
    fun givenAnAddedEvent_whenCheckingIfContains_thenShouldReturnTrue() = runTest {
        val event = TestEvent.memberJoin("testEvent")
        eventsBuffer.add(event)

        val result = eventsBuffer.contains(event)

        assertTrue(result)
    }

    @Test
    fun givenAnEventThatWasNotAdded_whenCheckingIfContains_thenShouldReturnFalse() = runTest {
        val event = TestEvent.memberJoin("testEvent")

        val result = eventsBuffer.contains(event)

        assertFalse(result)
    }

    @Test
    fun givenAnAddedEvent_whenRemovingIt_thenShouldReturnTrue() = runTest {
        val event = TestEvent.memberJoin("testEvent")
        eventsBuffer.add(event)

        val result = eventsBuffer.remove(event)

        assertTrue(result)
    }

    @Test
    fun givenAnAddedEvent_whenRemovingIt_thenShouldNoLongerContainThatEvent() = runTest {
        val event = TestEvent.memberJoin("testEvent")
        eventsBuffer.add(event)

        eventsBuffer.remove(event)

        assertFalse { eventsBuffer.contains(event) }
    }


    @Test
    fun givenAnEventThatWasNotAdded_whenRemovingIt_thenShouldReturnFalse() = runTest {
        val event = TestEvent.memberJoin("testEvent")

        val result = eventsBuffer.remove(event)

        assertFalse(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithLastEvent_thenShouldReturnTrue() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        val result = eventsBuffer.clearBufferIfLastEventEquals(event2)

        assertTrue(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithLastEvent_thenNoEventsShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        eventsBuffer.clearBufferIfLastEventEquals(event2)

        assertFalse { eventsBuffer.contains(event1) }
        assertFalse { eventsBuffer.contains(event2) }
    }

    @Test
    fun givenInsertedEvents_whenClearingBuffer_thenNoEventShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        eventsBuffer.clear()

        assertFalse { eventsBuffer.contains(event1) }
        assertFalse { eventsBuffer.contains(event2) }
    }
}
