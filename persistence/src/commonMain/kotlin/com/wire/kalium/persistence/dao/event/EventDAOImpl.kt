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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.EventsQueries
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.throttleLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.seconds

class EventDAOImpl(
    private val eventsQueries: EventsQueries,
    private val queriesContext: CoroutineContext
) : EventDAO {

    override suspend fun observeEvents(fromIdExclusive: Long): Flow<List<EventEntity>> {
        return eventsQueries.selectAll(::mapEvent)
            .asFlow()
            .flowOn(queriesContext)
            .mapToList()
    }

    override suspend fun observeUnprocessedEvents(): Flow<List<EventEntity>> {
        return eventsQueries.selectUnprocessedEvents(::mapEvent)
            .asFlow()
            .flowOn(queriesContext)
            .throttleLatest(2.seconds) // Throttle to avoid too frequent updates
            .mapToList()
    }

    override suspend fun insertEvents(events: List<NewEventEntity>) {
        withContext(queriesContext) {
            events.forEach { event ->
                eventsQueries.insertOrIgnoreEvent(
                    event.eventId,
                    0,
                    event.payload,
                    if (event.isLive) 1L else 0L
                )
            }
        }
    }

    override suspend fun deleteProcessedEventsBefore(id: Long) {
        withContext(queriesContext) {
            eventsQueries.deleteProcessedEventsBefore(id)
        }
    }

    override suspend fun deleteAllProcessedEvents() {
        withContext(queriesContext) {
            eventsQueries.deleteAllProcessedEvents()
        }
    }

    override suspend fun getEventById(id: String): EventEntity? {
        return withContext(queriesContext) {
            eventsQueries.getById(id, ::mapEvent)
                .executeAsOneOrNull()
        }
    }

    override suspend fun markEventAsProcessed(
        eventId: String,
    ) {
        withContext(queriesContext) {
            eventsQueries.markEventAsProcessed(eventId)
        }
    }

    override suspend fun setAllUnprocessedEventsAsPending() {
        withContext(queriesContext) {
            eventsQueries.setAllUnprocessedEventsAsPending()
        }
    }

    private fun mapEvent(
        id: Long,
        eventId: String,
        isProcessed: Long,
        payload: String,
        isLive: Long
    ): EventEntity {
        return EventEntity(
            id = id,
            eventId = eventId,
            isProcessed = isProcessed != 0L,
            payload = payload,
            isLive = isLive != 0L
        )
    }
}
