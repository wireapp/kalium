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
    fun givenAnAddedEvent_whenCheckingIfPresent_thenShouldReturnTrue() = runTest {
        val event = TestEvent.memberJoin("testEvent")
        eventsBuffer.addToBuffer(event)

        val result = eventsBuffer.isEventPresentInBuffer(event)

        assertTrue(result)
    }

    @Test
    fun givenAnEventThatWasNotAdded_whenCheckingIfPresent_thenShouldReturnFalse() = runTest {
        val event = TestEvent.memberJoin("testEvent")

        val result = eventsBuffer.isEventPresentInBuffer(event)

        assertFalse(result)
    }


    @Test
    fun givenAnAddedEvent_whenRemovingIt_thenShouldReturnTrue() = runTest {
        val event = TestEvent.memberJoin("testEvent")
        eventsBuffer.addToBuffer(event)

        val result = eventsBuffer.removeEventFromBuffer(event)

        assertTrue(result)
    }

    @Test
    fun givenAnEventThatWasNotAdded_whenRemovingIt_thenShouldReturnFalse() = runTest {
        val event = TestEvent.memberJoin("testEvent")

        val result = eventsBuffer.removeEventFromBuffer(event)

        assertFalse(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithLastEvent_thenShouldReturnTrue() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.addToBuffer(event1)
        eventsBuffer.addToBuffer(event2)

        val result = eventsBuffer.clearBufferIfLastEventEquals(event2)

        assertTrue(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithLastEvent_thenNoEventsShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.addToBuffer(event1)
        eventsBuffer.addToBuffer(event2)

        eventsBuffer.clearBufferIfLastEventEquals(event2)

        assertFalse { eventsBuffer.isEventPresentInBuffer(event1) }
        assertFalse { eventsBuffer.isEventPresentInBuffer(event2) }
    }

    @Test
    fun givenInsertedEvents_whenClearingBuffer_thenNoEventShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.addToBuffer(event1)
        eventsBuffer.addToBuffer(event2)

        eventsBuffer.clearBuffer()

        assertFalse { eventsBuffer.isEventPresentInBuffer(event1) }
        assertFalse { eventsBuffer.isEventPresentInBuffer(event2) }
    }
}
