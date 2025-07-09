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
import com.wire.kalium.logic.data.event.EventVersion
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import io.ktor.utils.io.errors.IOException
import io.mockative.Mockable
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.datetime.toInstant

/**
 * Orchestrates the reception of events from remote sources,
 * coordinating between historical (pending) and real-time (live) event delivery modes.
 *
 * In environments with async notifications enabled, it emits only real-time events from WebSocket.
 * In legacy environments, it first fetches pending events and then transitions to live updates.
 *
 */
@Mockable
internal interface EventGatherer {

    /**
     * Establishes a WebSocket connection and starts receiving events in real time.
     * Waits until an initial connection is established before starting
     * to emit all unprocessed events stored locally.
     *
     * This flow is hot and updated based on the internal event trigger mechanism.
     *
     * Once the local event storage is exhausted, it will emit [EventStreamData.IsUpToDate], signaling
     * that the app is up-to-date with the remote sources.
     */
    suspend fun gatherEvents(): Flow<EventStreamData>

}

internal class EventGathererImpl(
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider,
    private val eventRepository: EventRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val logger = logger.withFeatureId(SYNC)

    // TODO handle multiple events at once
    override suspend fun gatherEvents(): Flow<EventStreamData> = channelFlow {
        coroutineScope {
            val waitForReceivingSetupJob = Job()
            launch {
                // TODO(refactor): stop emitting Unit, emit status of the underlying event fetching for clarity
                receiveEventsFromRemoteAndInsertIntoLocalStorage().onEach { waitForReceivingSetupJob.complete() }.collect()
            }
            launch {
                waitForReceivingSetupJob.join()
                eventRepository.observeEvents().onEach { events ->
                    kaliumLogger.d("$TAG gathering ${events.size} events")
                }.collect { events ->
                    if (events.isEmpty()) {
                        send(EventStreamData.IsUpToDate)
                    } else {
                        send(EventStreamData.NewEvents(events))
                    }
                }
            }
        }
    }

    private suspend fun receiveEventsFromRemoteAndInsertIntoLocalStorage(): Flow<Unit> = flow {
        /**
         * Fetches and saves live events and pending based on whether the client supports async notifications.
         * Throws [KaliumSyncException] if event retrieval fails.
         */
        val isAsyncNotifications = isClientAsyncNotificationsCapableProvider.isClientAsyncNotificationsCapable()
        fetchEventFlow(isAsyncNotifications)
            .onSuccess { emitEvents(it) }
            .onFailure { throw KaliumSyncException("Failure when receiving events", it) }
    }

    /**
     * Retrieves the event flow based on async notification capability.
     */
    private suspend fun fetchEventFlow(isAsyncNotifications: Boolean) = if (isAsyncNotifications) {
        eventRepository.liveEvents()
    } else {
        // in the old system we fetch pending events from the notification stream based on last saved event id
        eventRepository.lastSavedEventId().flatMap {
            eventRepository.liveEvents()
        }.mapLeft {
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
        is WebSocketEvent.Open -> onWebSocketOpen()
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived()
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private suspend fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<EventVersion>) {
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }
    }

    private suspend fun FlowCollector<Unit>.onWebSocketEventReceived() {
        emit(Unit)
    }

    private suspend fun FlowCollector<Unit>.onWebSocketOpen() {
        logger.i("Websocket Open")
        // TODO: Handle time drift in a different way, e.g. the notification api is already called
        //  somewhere else so maybe we can take the time from there ?
//          handleTimeDrift()
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
