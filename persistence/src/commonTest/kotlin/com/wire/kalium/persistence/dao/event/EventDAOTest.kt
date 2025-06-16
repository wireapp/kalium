/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.event

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class EventDAOTest : BaseDatabaseTest() {

    private lateinit var dao: EventDAO
    private val selfUserId = UserIDEntity("self", "domain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        dao = db.eventDAO
    }

    @Test
    fun givenNoEventsInserted_whenObservingUnprocessed_shouldReturnEmpty() = runTest {
        val result = dao.observeUnprocessedEvents().first()
        assertTrue(result.isEmpty(), "Expected no unprocessed events, but found: $result")
    }

    @Test
    fun givenUnprocessedEventInserted_whenObservingUnprocessed_shouldReturnEvent() = runTest {
        val event = NewEventEntity(eventId = "e1", payload = "{\"test\":true}", isLive = false)
        dao.insertEvents(listOf(event))

        val result = dao.observeUnprocessedEvents().first()

        assertEquals(1, result.size)
        with(result.first()) {
            assertEquals("e1", eventId)
            assertFalse(isProcessed, "Expected event to be unprocessed")
            assertEquals("{\"test\":true}", payload)
        }
    }

    @Test
    fun givenEventInserted_whenMarkingAsProcessed_thenShouldBeExcludedFromUnprocessedFlow() = runTest {
        val event = NewEventEntity(eventId = "e2", payload = "{}", isLive = false)
        dao.insertEvents(listOf(event))

        dao.markEventAsProcessed("e2")

        val result = dao.observeUnprocessedEvents().first()
        assertTrue(result.none { it.eventId == "e2" })
    }

    @Test
    fun givenMultipleEventsMarkedProcessed_whenDeletingAllProcessed_thenOnlyUnprocessedRemain() = runTest {
        val events = listOf(
            NewEventEntity(eventId = "e1", payload = "{}", isLive = false),
            NewEventEntity(eventId = "e2", payload = "{}", isLive = false),
            NewEventEntity(eventId = "e3", payload = "{}", isLive = false)
        )
        dao.insertEvents(events)
        dao.markEventAsProcessed("e1")
        dao.markEventAsProcessed("e2")

        dao.deleteAllProcessedEvents()

        val result = dao.observeEvents(0).first()
        assertEquals(1, result.size)
        assertEquals("e3", result.first().eventId)
    }

    @Test
    fun givenEventsWithDifferentIds_whenGettingById_shouldReturnCorrectEvent() = runTest {
        val e1 = NewEventEntity(eventId = "eventA", payload = "{\"a\":1}", isLive = false)
        val e2 = NewEventEntity(eventId = "eventB", payload = "{\"b\":2}", isLive = false)
        dao.insertEvents(listOf(e1, e2))

        val result = dao.getEventById("eventB")

        assertNotNull(result)
        assertEquals("eventB", result.eventId)
        assertEquals("{\"b\":2}", result.payload)
    }

    @Test
    fun givenProcessedEventsBeforeId_whenDeletingBefore_shouldRemoveOnlyThose() = runTest {
        dao.insertEvents(
            listOf(
                NewEventEntity(eventId = "e1", payload = "{}", isLive = false),
                NewEventEntity(eventId = "e2", payload = "{}", isLive = false),
                NewEventEntity(eventId = "e3", payload = "{}", isLive = false)
            )
        )
        dao.markEventAsProcessed("e1")
        dao.markEventAsProcessed("e2")

        dao.deleteProcessedEventsBefore(3)

        val result = dao.observeEvents(0).first()
        val ids = result.map { it.eventId }

        assertEquals(listOf("e3"), ids)
    }

    @Test
    fun givenMultipleEventsInserted_whenObservingEvents_shouldReturnAll() = runTest {
        val events = listOf(
            NewEventEntity(eventId = "e1", payload = "{}", isLive = false),
            NewEventEntity(eventId = "e2", payload = "{}", isLive = false),
            NewEventEntity(eventId = "e3", payload = "{}", isLive = false)
        )
        dao.insertEvents(events)

        val result = dao.observeEvents(fromIdExclusive = 0).first()
        val ids = result.map { it.eventId }

        assertEquals(3, result.size)
        assertEquals(listOf("e1", "e2", "e3"), ids)
    }

    @Test
    fun givenDuplicateEventIds_whenInserting_thenShouldIgnoreDuplicates() = runTest {
        val event = NewEventEntity(eventId = "duplicate", payload = "{\"x\":1}", isLive = false)
        dao.insertEvents(listOf(event))
        dao.insertEvents(listOf(event))

        val result = dao.observeEvents(0).first()
        assertEquals(1, result.size)
        assertEquals("duplicate", result.first().eventId)
    }

    @Test
    fun givenNonexistentEventId_whenGettingById_shouldReturnNull() = runTest {
        val result = dao.getEventById("nope")
        assertEquals(null, result)
    }

    @Test
    fun givenOnlyUnprocessedEvents_whenDeletingProcessed_thenNoneShouldBeDeleted() = runTest {
        val events = listOf(
            NewEventEntity(eventId = "a", payload = "{}", isLive = false),
            NewEventEntity(eventId = "b", payload = "{}", isLive = false)
        )
        dao.insertEvents(events)

        dao.deleteAllProcessedEvents()

        val result = dao.observeEvents(0).first()
        assertEquals(2, result.size)
    }

    @Test
    fun givenUnprocessedEvent_whenMarkingAsProcessed_thenShouldBeMarked() = runTest {
        val event = NewEventEntity(eventId = "z", payload = "{}", isLive = false)
        dao.insertEvents(listOf(event))

        dao.markEventAsProcessed("z")

        val updated = dao.getEventById("z")
        assertNotNull(updated)
        assertTrue(updated.isProcessed)
    }

}
