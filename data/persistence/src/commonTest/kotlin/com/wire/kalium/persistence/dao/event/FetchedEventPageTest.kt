/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.persistence.dao.event

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FetchedEventPageTest : BaseDatabaseTest() {
    @Test
    fun laterRowAndCursorFailuresRollBackPageReplacementTogether() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("app-page-rollback", "wire.test"), encryptedDBSecret, true)
        try {
            val dao = database.eventDAO
            dao.insertEvents(listOf(event("pending"), event("live", live = true), event("processed")))
            dao.markEventAsProcessed("processed")
            database.metadataDAO.insertValue("anchor", NotificationEventStore.FETCH_CURSOR_KEY)
            val before = dao.observeEvents(0).first()
            val page = listOf(event("live").copy(payload = "new"), event("new"), event("processed"))
            for (failure in listOf("INSERT ON Events WHEN NEW.event_id = 'new'", "INSERT ON Metadata")) {
                database.sqlDriver.execute(null, "CREATE TRIGGER fail_page BEFORE $failure BEGIN SELECT RAISE(ABORT, 'page failed'); END", 0)
                assertFails { dao.storeFetchedPage(page) }
                assertEquals(before, dao.observeEvents(0).first())
                assertEquals("anchor", database.metadataDAO.valueByKey(NotificationEventStore.FETCH_CURSOR_KEY))
                database.sqlDriver.execute(null, "DROP TRIGGER fail_page", 0)
            }
            dao.storeFetchedPage(page)
            assertEquals(listOf("pending", "processed", "live", "new"), dao.observeEvents(0).first().map { it.eventId })
            assertFalse(checkNotNull(dao.getEventById("live")).isLive)
            assertTrue(checkNotNull(dao.getEventById("processed")).isProcessed)
            assertEquals("processed", database.metadataDAO.valueByKey(NotificationEventStore.FETCH_CURSOR_KEY))
        } finally {
            database.close()
        }
    }

    @Test
    fun appOverlapAndTransientAndNullPayloadCursorPoliciesRemainUnchanged() = runTest(dispatcher) {
        val database = createDatabase(UserIDEntity("app-page-policy", "wire.test"), encryptedDBSecret, true)
        try {
            val dao = database.eventDAO
            dao.insertEvents(listOf(event("pending"), event("live", live = true), event("processed")))
            dao.markEventAsProcessed("processed")
            val pending = dao.getEventById("pending")
            val processed = dao.getEventById("processed")
            dao.storeFetchedPage(listOf(event("live"), event("pending"), event("processed"), event("transient", transient = true)))
            assertEquals(pending, dao.getEventById("pending"))
            assertEquals(processed, dao.getEventById("processed"))
            assertEquals(listOf("pending", "processed", "live", "transient"), dao.observeEvents(0).first().map { it.eventId })
            assertEquals("processed", database.metadataDAO.valueByKey(NotificationEventStore.FETCH_CURSOR_KEY))
            dao.storeFetchedPage(emptyList())
            dao.storeFetchedPage(listOf(event("only-transient", transient = true)))
            assertEquals("processed", database.metadataDAO.valueByKey(NotificationEventStore.FETCH_CURSOR_KEY))
            dao.storeFetchedPage(listOf(event("null-payload").copy(payload = null)))
            assertNull(dao.getEventById("null-payload"))
            assertEquals("null-payload", database.metadataDAO.valueByKey(NotificationEventStore.FETCH_CURSOR_KEY))
            assertEquals("pending", database.notificationEventStore.nextPending()?.eventId)
        } finally {
            database.close()
        }
    }

    private fun event(id: String, live: Boolean = false, transient: Boolean = false) = NewEventEntity(id, "[]", transient, live)
}
