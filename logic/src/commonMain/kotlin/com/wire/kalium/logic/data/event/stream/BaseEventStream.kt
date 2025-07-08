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
package com.wire.kalium.logic.data.event.stream

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal abstract class BaseEventStream(
    private val eventDAO: EventDAO,
    private val selfUserId: UserId,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
) : EventStream {

    private var hasStarted = false
    private val mutex = Mutex()

    abstract suspend fun eventStreamStatusFlow(): Flow<EventStreamStatus>

    protected suspend fun insertNewEventsIntoStorage(
        newEvents: List<EventResponse>
    ): Either<CoreFailure, Unit> {
        val entities = newEvents.mapNotNull { event ->
            event.payload?.let {
                NewEventEntity(
                    eventId = event.id, payload = KtxSerializer.json.encodeToString(event), isLive = false
                )
            }
        }

        return wrapStorageRequest {
            eventDAO.insertEvents(entities)
        }.onSuccess {
            newEvents.lastOrNull { !it.transient }?.let {
                TODO("Update LastSavedEventID")
//                 updateLastSavedEventId(it.id)
            }
        }
    }

    protected fun Flow<EventStreamStatus>.mapToEventPage(): Flow<EventStreamData> {
        suspend fun pendingEventFlow(): Flow<List<EventEnvelope>> {
            return eventDAO.observeUnprocessedEvents().map { events ->
                events.map { eventEntity ->
                    eventEntity.eventId
                    val payload = KtxSerializer.json.decodeFromString<EventResponse>(eventEntity.payload)
                    eventMapper.fromDTO(payload, isLive = eventEntity.isLive)
                }.flatten()
            }
        }
        var isLive = false
        return transformLatest { eventStreamStatus ->
            when (eventStreamStatus) {
                EventStreamStatus.Initializing -> {
                    /** Nothing. We should just wait.  */
                }

                EventStreamStatus.CatchingUp -> {
                    pendingEventFlow().collect { events ->
                        emit(EventStreamData.NewEvents(events))
                    }
                    // Safe to process events. All of them should be treated as pending
                }

                is EventStreamStatus.Live -> {
                    pendingEventFlow().collect { events ->
                        emit(EventStreamData.NewEvents(events))
                    }
                }

                EventStreamStatus.ClosedRemotely -> TODO("NOPE. This should never happen")
            }
        }.transformLatest { eventStreamData ->
            eventStreamData.eventList
        }
    }

    final override suspend fun eventFlow(): Flow<EventStreamData> {
        mutex.withLock {
            check(!hasStarted) {
                "This Stream '$this' was already consumed! Can't be collected twice!"
            }
            hasStarted = true
        }
        return eventStreamStatusFlow().onStart { onInitialize() }.mapToEventPage()
    }

    protected suspend fun FlowCollector<EventStreamStatus>.onInitialize() {
        emit(EventStreamStatus.Initializing)
        setAllUnprocessedEventsAsPending().onFailure {
            throw KaliumSyncException("Failure to set all unprocessed events as pending", it)
        }
    }

    private suspend fun setAllUnprocessedEventsAsPending(): Either<CoreFailure, Unit> = wrapStorageRequest {
        eventDAO.setAllUnprocessedEventsAsPending()
    }

    protected suspend fun FlowCollector<EventStreamStatus>.onClose() {
        emit(EventStreamStatus.ClosedRemotely)
    }
}
