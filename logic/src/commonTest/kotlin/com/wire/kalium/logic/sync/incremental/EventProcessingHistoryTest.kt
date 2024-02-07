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
import com.wire.kalium.logic.framework.TestEvent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

class EventProcessingHistoryTest {

    private lateinit var eventsBuffer: EventProcessingHistory

    @BeforeTest
    fun setup() {
        eventsBuffer = EventProcessingHistory()
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

        val result = eventsBuffer.clearHistoryIfLastEventEquals(event2)

        assertTrue(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithLastEvent_thenNoEventsShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        eventsBuffer.clearHistoryIfLastEventEquals(event2)

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

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithOlderEvent_thenShouldReturnFalse() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        val result = eventsBuffer.clearHistoryIfLastEventEquals(event1)

        assertFalse(result)
    }

    @Test
    fun givenMultipleAddedEvents_whenClearingIfItsLastOneWithOlderEvent_thenAllEventsShouldBePresent() = runTest {
        val event1 = TestEvent.memberJoin("test1")
        val event2 = TestEvent.memberJoin("test2")
        eventsBuffer.add(event1)
        eventsBuffer.add(event2)

        eventsBuffer.clearHistoryIfLastEventEquals(event1)

        assertTrue { eventsBuffer.contains(event1) }
        assertTrue { eventsBuffer.contains(event2) }
    }

    @Test
    @Ignore // Benchmark
    fun measureContainsTimeOverLargeAmountOfEvents() = runTest(timeout = 100.seconds) {
        val allEvents = mutableListOf<Event>()
        val baseEvent = TestEvent.newConversationEvent()
        val eventCount = 100_000
        repeat(eventCount) {
            val newEvent = baseEvent.copy(id = "test_$it")
            allEvents.add(newEvent)
            eventsBuffer.add(newEvent)
        }

        val timeTaken = measureTime {
            allEvents.forEach {
                eventsBuffer.contains(it)
            }
        }
        println("Time taken to check $eventCount events: $timeTaken")
    }
}
