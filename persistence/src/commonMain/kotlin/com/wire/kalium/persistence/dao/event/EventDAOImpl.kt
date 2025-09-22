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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

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
            .mapToList()
    }

    override suspend fun getUnprocessedEvents(): List<EventEntity> = withContext(queriesContext) {
        eventsQueries.selectUnprocessedEvents(::mapEvent).executeAsList()
    }

    override suspend fun deleteUnprocessedLiveEventsByIds(ids: List<String>) = withContext(queriesContext) {
        eventsQueries.deleteUnprocessedLiveEventsByIds(ids)
    }

    override suspend fun deleteUnprocessedTransientEvents() = withContext(queriesContext) {
        eventsQueries.deleteUnprocessedTransientEvents()
    }

    override suspend fun insertEvents(events: List<NewEventEntity>) {
        withContext(queriesContext) {
            eventsQueries.transaction {
                events.forEach { event ->
                    eventsQueries.insertOrIgnoreEvent(
                        event_id = event.eventId,
                        is_processed = 0,
                        payload = event.payload,
                        is_live = if (event.isLive) 1L else 0L,
                        transient = event.transient
                    )
                }
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

    @Suppress("LongParameterList")
    private fun mapEvent(
        id: Long,
        eventId: String,
        isProcessed: Long,
        payload: String?,
        transient: Boolean,
        isLive: Long
    ): EventEntity {
        return EventEntity(
            id = id,
            eventId = eventId,
            isProcessed = isProcessed != 0L,
            payload = payload,
            isLive = isLive != 0L,
            transient = transient
        )
    }
}
