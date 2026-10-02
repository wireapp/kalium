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

import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext

/** Stores complete REST pages and their fetch cursor in one SQLite transaction. */
class NotificationEventStore internal constructor(
    private val database: UserDatabase,
    private val readDispatcher: ReadDispatcher,
    private val writeDispatcher: WriteDispatcher,
) {
    suspend fun cursor(): String? = withContext(readDispatcher.value) {
        database.metadataQueries.selectValueByKey(FETCH_CURSOR_KEY).awaitAsOneOrNull()
    }

    suspend fun nextPending(): EventEntity? = withContext(readDispatcher.value) {
        database.eventsQueries.selectUnprocessedEvents(1) { id, eventId, processed, payload, transient, live ->
            EventEntity(id, eventId, processed != 0L, payload, transient, live != 0L)
        }.awaitAsOneOrNull()
    }

    /** Read-only terminal-account preflight; never advances the processing cursor. */
    suspend fun pendingAfter(afterId: Long, pageSize: Long = 100): List<EventEntity> = withContext(readDispatcher.value) {
        require(pageSize in 1..MAX_PENDING_PAGE_SIZE)
        database.eventsQueries.selectUnprocessedEventsAfter(afterId, pageSize) { id, eventId, processed, payload, transient, live ->
            EventEntity(id, eventId, processed != 0L, payload, transient, live != 0L)
        }.awaitAsList()
    }

    /** Acknowledge received events and retire their operation records in the same SQL commit. */
    suspend fun markProcessed(
        eventIds: List<String>,
        metadataKeysByEvent: Map<String, List<String>>,
    ) = withContext(writeDispatcher.value) {
        require(metadataKeysByEvent.keys.all { it in eventIds })
        database.transaction {
            // Missing history is not proof that an outstanding operation was acknowledged.
            // Only recovery-bearing events need this check; ordinary markers add no reads.
            metadataKeysByEvent.keys.forEach { eventId ->
                check(database.eventsQueries.getById(eventId).awaitAsOneOrNull() != null) {
                    "Cannot retire an operation without its event"
                }
            }
            eventIds.forEach { database.eventsQueries.markEventAsProcessed(it) }
            metadataKeysByEvent.values.flatten().distinct().forEach { database.metadataQueries.deleteValue(it) }
        }
    }

    suspend fun storePage(expectedCursor: String?, events: List<NewEventEntity>) = withContext(writeDispatcher.value) {
        require(events.all { !it.isLive && it.eventId.isNotBlank() })
        require(events.map { it.eventId }.distinct().size == events.size)
        require(events.none { it.eventId == expectedCursor }) { "Notification page includes its exclusive cursor" }
        database.transaction {
            check(database.metadataQueries.selectValueByKey(FETCH_CURSOR_KEY).awaitAsOneOrNull() == expectedCursor) {
                "Notification cursor changed"
            }
            events.forEach { event ->
                check(database.eventsQueries.getById(event.eventId).awaitAsOneOrNull() == null) {
                    "Notification page overlaps stored history"
                }
                database.eventsQueries.insertOrIgnoreEvent(event.eventId, 0, event.payload, 0, event.transient)
            }
            events.lastOrNull { !it.transient }?.let { database.metadataQueries.insertValue(FETCH_CURSOR_KEY, it.eventId) }
        }
    }

    companion object {
        private const val MAX_PENDING_PAGE_SIZE = 1000L

        // Existing app key: this is the fetched/stored cursor despite its historical name.
        const val FETCH_CURSOR_KEY = "last_processed_event_id"
    }
}
