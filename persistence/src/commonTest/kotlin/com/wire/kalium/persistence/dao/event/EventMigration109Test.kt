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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventMigration109Test : BaseDatabaseTest() {

    private lateinit var eventDAO: EventDAO
    private lateinit var metadataDAO: com.wire.kalium.persistence.dao.MetadataDAO
    private lateinit var userDatabase: com.wire.kalium.persistence.db.UserDatabaseBuilder
    private val selfUserId = UserIDEntity("migration", "test")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        userDatabase = createDatabase(selfUserId, encryptedDBSecret, true)
        eventDAO = userDatabase.eventDAO
        metadataDAO = userDatabase.metadataDAO
    }

    private fun runMigration109Query() {
        // This is the exact query from 109/sqm.sq
        val updateQuery = """
            UPDATE Metadata 
            SET stringValue = COALESCE(
                (SELECT event_id 
                 FROM Events 
                 WHERE is_processed = 1 
                 ORDER BY id DESC 
                 LIMIT 1),
                (SELECT event_id 
                 FROM Events 
                 WHERE is_processed = 0 
                 ORDER BY id ASC 
                 LIMIT 1)
            )
            WHERE key = 'last_processed_event_id' 
            AND EXISTS (SELECT 1 FROM Events)
        """.trimIndent()
        
        val deleteQuery = "DELETE FROM Events"
        
        // Execute the migration queries using the SqlDriver
        userDatabase.sqlDriver.execute(null, updateQuery, 0)
        userDatabase.sqlDriver.execute(null, deleteQuery, 0)
    }

    @Test
    fun givenEmptyEventsTable_whenRunningMigration_thenLastProcessedEventIdShouldNotBeUpdated() = runTest(dispatcher) {
        // Given: metadata key exists but Events table is empty
        metadataDAO.insertValue("existing_value", "last_processed_event_id")
        
        // When: running migration
        runMigration109Query()
        
        // Then: last_processed_event_id should remain unchanged
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("existing_value", result)
    }

    @Test
    fun givenOnlyProcessedEvents_whenRunningMigration_thenShouldUseLatestProcessedEventId() = runTest(dispatcher) {
        // Given: setup metadata key and processed events
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "event1", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event2", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event3", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        
        // Mark events as processed (note: event2 and event3 are processed, event1 is not)
        eventDAO.markEventAsProcessed("event1")
        eventDAO.markEventAsProcessed("event3")
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the latest processed event (highest ID)
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event3", result) // event3 has higher ID than event1
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenOnlyUnprocessedEvents_whenRunningMigration_thenShouldUseEarliestUnprocessedEventId() = runTest(dispatcher) {
        // Given: setup metadata key and unprocessed events
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "event1", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event2", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event3", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        // Note: not marking any as processed
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the earliest unprocessed event (lowest ID)
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event1", result) // event1 has the lowest ID
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenMixedProcessedAndUnprocessedEvents_whenRunningMigration_thenShouldPreferLatestProcessedEvent() = runTest(dispatcher) {
        // Given: setup metadata key and mixed events
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "event1", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event2", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event3", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event4", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        
        // Mark some events as processed
        eventDAO.markEventAsProcessed("event1")
        eventDAO.markEventAsProcessed("event2")
        // event3 and event4 remain unprocessed
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the latest processed event (event2), not the earliest unprocessed (event3)
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event2", result)
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenNoMetadataKeyExists_whenRunningMigration_thenShouldNotCreateKey() = runTest(dispatcher) {
        // Given: Events exist but no metadata key
        val events = listOf(
            NewEventEntity(eventId = "event1", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        eventDAO.markEventAsProcessed("event1")
        
        // When: running migration
        runMigration109Query()
        
        // Then: metadata key should not be created
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertNull(result)
        
        // And: Events table should still be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenLargeNumberOfEvents_whenRunningMigration_thenShouldHandleCorrectly() = runTest(dispatcher) {
        // Given: setup metadata key and many events
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = buildList {
            repeat(1000) { i ->
                add(NewEventEntity(eventId = "event$i", payload = "{}", isLive = false, transient = false))
            }
        }
        eventDAO.insertEvents(events)
        
        // Mark first 500 as processed
        repeat(500) { i ->
            eventDAO.markEventAsProcessed("event$i")
        }
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the latest processed event
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event499", result) // event499 is the last processed event
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenEventIdsWithSpecialCharacters_whenRunningMigration_thenShouldHandleCorrectly() = runTest(dispatcher) {
        // Given: setup metadata key and events with special IDs
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "event-with-dashes", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event_with_underscores", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event.with.dots", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event@with@symbols", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        
        // Mark the last one as processed
        eventDAO.markEventAsProcessed("event@with@symbols")
        
        // When: running migration
        runMigration109Query()
        
        // Then: should handle special characters correctly
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event@with@symbols", result)
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }

    @Test
    fun givenEmptyEventsTableAndNoMetadataKey_whenRunningMigration_thenShouldNotCreateKey() = runTest(dispatcher) {
        // Given: Events table is empty and no metadata key exists
        // (No setup needed - both tables are empty by default)
        
        // When: running migration
        runMigration109Query()
        
        // Then: metadata key should not be created
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertNull(result)
    }

    @Test
    fun givenAllEventsAreProcessed_whenRunningMigration_thenShouldUseLatestProcessedEvent() = runTest(dispatcher) {
        // Given: setup metadata key and all events are processed
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "event1", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event2", payload = "{}", isLive = false, transient = false),
            NewEventEntity(eventId = "event3", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        
        // Mark ALL events as processed
        eventDAO.markEventAsProcessed("event1")
        eventDAO.markEventAsProcessed("event2")
        eventDAO.markEventAsProcessed("event3")
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the latest processed event (highest ID)
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("event3", result) // event3 has the highest ID
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }
    
    @Test
    fun givenSingleEvent_whenRunningMigration_thenShouldHandleCorrectly() = runTest(dispatcher) {
        // Given: setup metadata key and single event
        metadataDAO.insertValue("old_value", "last_processed_event_id")
        
        val events = listOf(
            NewEventEntity(eventId = "single-event", payload = "{}", isLive = false, transient = false)
        )
        eventDAO.insertEvents(events)
        // Leave event unprocessed
        
        // When: running migration
        runMigration109Query()
        
        // Then: should use the single unprocessed event
        val result = metadataDAO.valueByKeyFlow("last_processed_event_id").first()
        assertEquals("single-event", result)
        
        // And: Events table should be empty
        val eventsAfter = eventDAO.observeEvents(0).first()
        assertTrue(eventsAfter.isEmpty())
    }
}
