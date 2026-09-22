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
package com.wire.kalium.persistence.dao.event

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull

class NotificationEventStoreTest : BaseDatabaseTest() {
    @Test
    fun completePagesIncludeUnknownPayloadsAndAdvanceTheCursorOnlyWithEveryRow() = runTest(dispatcher) {
        val userId = UserIDEntity("page", "wire.test")
        val database = createDatabase(userId, encryptedDBSecret, true)
        try {
            val events = database.notificationEventStore
            database.sqlDriver.execute(
                null,
                "CREATE TRIGGER fail_page BEFORE INSERT ON Events WHEN NEW.event_id = 'two' BEGIN SELECT RAISE(ABORT, 'page failed'); END",
                0
            )
            val page = listOf(NewEventEntity("one", "[]", false, false), NewEventEntity("two", null, false, false))
            assertFails { events.storePage(null, page) }
            assertNull(events.cursor())
            assertNull(events.nextPending())
            database.sqlDriver.execute(null, "DROP TRIGGER fail_page", 0)
            events.storePage(null, page)
            assertEquals("two", events.cursor())
            assertEquals("one", events.nextPending()?.eventId)
            database.eventDAO.markEventAsProcessed("one")
            assertEquals("two", events.nextPending()?.eventId)
            assertNull(events.nextPending()?.payload)
            assertFails { events.storePage(null, listOf(NewEventEntity("three", "[]", false, false))) }
            assertNull(database.eventDAO.getEventById("three"))
            assertEquals("two", events.cursor())
        } finally {
            database.close()
        }
    }

    @Test
    fun overlappingPagesCannotMoveTheCursorOrLeaveAPartialOrderedPrefix() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("overlap-page", "wire.test"), encryptedDBSecret, true)
        try {
            val store = database.notificationEventStore
            fun event(id: String) = NewEventEntity(id, "[]", false, false)
            store.storePage(null, listOf(event("one"), event("two")))
            database.eventDAO.markEventsAsProcessed(listOf("one", "two"))
            for (ids in listOf(listOf("three", "three"), listOf("two", "three"), listOf("three", "two"),
                listOf("one", "three"), listOf("three", "one"))) {
                assertFails { store.storePage("two", ids.map(::event)) }
                assertEquals("two", store.cursor())
                assertNull(database.eventDAO.getEventById("three"))
                assertNull(store.nextPending())
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun transientRowsPersistInOrderWithoutBecomingFetchCursors() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("transient-page", "wire.test"), encryptedDBSecret, true)
        try {
            val store = database.notificationEventStore
            fun event(id: String, transient: Boolean) = NewEventEntity(id, "[]", transient, false)
            store.storePage(null, listOf(event("early-transient", true)))
            assertNull(store.cursor())
            store.storePage(null, listOf(event("anchor", false), event("transient", true)))
            assertEquals("anchor", store.cursor())
            store.storePage("anchor", listOf(event("only-transient", true)))
            assertEquals("anchor", store.cursor())
            for (id in listOf("early-transient", "anchor", "transient", "only-transient")) {
                assertEquals(id, store.nextPending()?.eventId)
                database.eventDAO.markEventAsProcessed(id)
            }
            assertNull(store.nextPending())
        } finally {
            database.close()
        }
    }

    @Test
    fun transientSuffixAndCursorFailuresRollBackTheCompletePage() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("transient-rollback", "wire.test"), encryptedDBSecret, true)
        try {
            val store = database.notificationEventStore
            store.storePage(null, listOf(NewEventEntity("anchor", "[]", false, false)))
            database.eventDAO.markEventAsProcessed("anchor")
            val page = listOf(NewEventEntity("next", "[]", false, false), NewEventEntity("transient", "[]", true, false))
            for (failure in listOf("INSERT ON Events WHEN NEW.event_id = 'transient'", "INSERT ON Metadata")) {
                database.sqlDriver.execute(null, "CREATE TRIGGER fail_page BEFORE $failure BEGIN SELECT RAISE(ABORT, 'failed'); END", 0)
                assertFails { store.storePage("anchor", page) }
                assertEquals("anchor", store.cursor())
                assertNull(store.nextPending())
                assertNull(database.eventDAO.getEventById("next"))
                assertNull(database.eventDAO.getEventById("transient"))
                database.sqlDriver.execute(null, "DROP TRIGGER fail_page", 0)
            }
            store.storePage("anchor", page)
            assertEquals("next", store.cursor())
        } finally {
            database.close()
        }
    }

    @Test
    fun appBacklogAndFetchedPagesShareTheExistingInsertionOrder() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("live-page", "wire.test"), encryptedDBSecret, true)
        try {
            database.eventDAO.insertEvents(listOf(NewEventEntity("z-live", "[]", false, true)))
            database.eventDAO.setAllUnprocessedEventsAsPending()
            val events = database.notificationEventStore
            events.storePage(null, listOf(NewEventEntity("a-fetched", "[]", false, false)))
            assertEquals("z-live", events.nextPending()?.eventId)
            database.eventDAO.markEventAsProcessed("z-live")
            assertEquals("a-fetched", events.nextPending()?.eventId)
        } finally {
            database.close()
        }
    }
}
