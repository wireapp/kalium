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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.event.stream.EventStreamData
import com.wire.kalium.logic.data.event.EventVersion
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import io.ktor.utils.io.errors.IOException
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.datetime.toInstant

/**
 * Orchestrates the reception of events from remote sources,
 * coordinating between historical (pending) and real-time (live) event delivery modes.
 *
 * In environments with async notifications enabled, it emits only real-time events from WebSocket.
 * In legacy environments, it first fetches pending events and then transitions to live updates.
 *
 * The current mode of delivery (PENDING or LIVE) is tracked via [currentSource].
 */
@Mockable
internal interface EventGatherer {

    /**
     * Establishes a WebSocket connection to start receiving real-time events.
     *
     * Emits all unprocessed events stored locally, and changes in current source, see [EventStreamData].
     * This flow is expected to emit in the following sequence:
     * 1. [EventStreamData.StatusChange], with [EventStreamData.StatusChange.isCatchingUp] = true.
     * 2. Pending events (if any exist).
     * 3. [EventStreamData.StatusChange], with [EventStreamData.StatusChange.isCatchingUp] = false as soon as pending events are finished
     * 4. Live events
     *
     * When `isAsyncNotifications = true`, this flow emits only live events.
     * When `false`, this flow starts with pending events and switches to live once completed.
     *
     * This flow is hot and updated based on the internal event trigger mechanism.
     */
    suspend fun gatherEvents(): Flow<EventStreamData>

}

internal class EventGathererImpl(
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider,
    private val eventRepository: EventRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    private val processingScope: CoroutineScope,
    private val liveSourceChangeHandler: LiveSourceChangeHandler = LiveSourceChangeHandlerImpl(processingScope),
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val logger = logger.withFeatureId(SYNC)

    // TODO handle multiple events at once
    override suspend fun gatherEvents(): Flow<EventStreamData> = flow {
        coroutineScope {
            launch {
                receiveEvents
            }
        }
        var hasEmittedLiveEvents = false
        emit(EventStreamData.StatusChange(EventSource.PENDING))
        eventRepository.observeEvents().collect { events ->
            emit(EventStreamData.Envelopes(events))

            val hasAnyLiveEvent = events.any { it.deliveryInfo.source == EventSource.LIVE }
            if (!hasEmittedLiveEvents && hasAnyLiveEvent) {
                hasEmittedLiveEvents = true
                emit(EventStreamData.StatusChange(EventSource.LIVE))
            }
            emit(EventStreamData.NewEnvelope(event))
        }
    }

    override suspend fun receiveEvents(): Flow<Unit> = flow {
        /**
         * Fetches and saves live events and pending based on whether the client supports async notifications.
         * Throws [KaliumSyncException] if event retrieval fails.
         */
        isClientAsyncNotificationsCapableProvider().flatMap { isAsyncNotifications ->
            fetchEventFlow(isAsyncNotifications)
                .onSuccess { emitEvents(it) }
                .onFailure { throw KaliumSyncException("Failure when receiving events", it) }
        }
    }

    /**
     * Retrieves the event flow based on async notification capability.
     */
    private suspend fun fetchEventFlow(isAsyncNotifications: Boolean) = if (isAsyncNotifications) {
        eventRepository.liveEvents()
    } else {
        // in the old system we fetch pending events from the notification stream based on last saved event id
        eventRepository.lastSavedEventId()
            .flatMap {
                eventRepository.liveEvents()
            }
            .mapLeft {
                when (it) {
                    is StorageFailure.DataNotFound -> // last saved event ID not found, perform slow sync again to get it
                        CoreFailure.SyncEventOrClientNotFound

                    else -> it
                }
            }
    }

    private suspend fun FlowCollector<Unit>.emitEvents(
        webSocketEventFlow: Flow<WebSocketEvent<EventVersion>>
    ) = webSocketEventFlow
        .buffer(Channel.UNLIMITED)
        .cancellable()
        .collect { handleWebsocketEvent(it) }

    private suspend fun FlowCollector<Unit>.handleWebsocketEvent(
        webSocketEvent: WebSocketEvent<EventVersion>
    ) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen(webSocketEvent.shouldProcessPendingEvents)
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private suspend fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<EventVersion>) {
        val eventVersion = webSocketEvent.payload ?: EventVersion.LEGACY
        if (eventVersion == EventVersion.ASYNC) {
            liveSourceChangeHandler.clear()
        }
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }
    }

    private suspend fun FlowCollector<Unit>.onWebSocketEventReceived(webSocketEvent: WebSocketEvent.BinaryPayloadReceived<EventVersion>) {
        val eventVersion: EventVersion = webSocketEvent.payload
        if (eventVersion == EventVersion.ASYNC) {
            liveSourceChangeHandler.scheduleNewCatchingUpJob(
                onEventIntervalGapReached = { _currentSource.value = EventSource.LIVE }
            )
        }
        logger.i("Websocket Binary payload received")
        emit(Unit)
    }

    private suspend fun FlowCollector<Unit>.onWebSocketOpen(shouldProcessPendingEvents: Boolean) {
        logger.i("Websocket Open")
        // TODO: Handle time drift in a different way, e.g. the notification api is already called
        //  somewhere else so maybe we can take the time from there ?
//          handleTimeDrift()
        if (!shouldProcessPendingEvents) {
            logger.i("Offline events collection skipped due to new system available. Collecting Live events.")
            liveSourceChangeHandler.startNewCatchingUpJob(onStartIntervalReached = { _currentSource.value = EventSource.LIVE })
        }
        emit(Unit)
    }

    private suspend fun handleTimeDrift() {
        eventRepository.fetchServerTime()?.let {
            serverTimeHandler.computeTimeOffset(it.toInstant().epochSeconds)
        }
    }

    companion object {
        const val MAX_EVENTS_TO_SWITCH_TO_LIVE = 5
        const val TAG = "[EventGatherer]"
    }
}
