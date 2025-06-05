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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatten
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.toInstant

/**
 * Responsible for fetching events from a remote source, orchestrating between events missed since
 * the last time we gathered events and new events being received in real time.
 */
internal interface EventGatherer {

    /**
     * Fetches events from remote sources, handling websocket opening and duplication of events from multiple sources.
     * - Opens Websocket
     * - Fetches missed events since last time online
     * - Emits missed events
     * - Updates status to Online
     * - Emits Websocket events as they come, omitting duplications.
     *
     */
    suspend fun gatherEvents(): Flow<EventEnvelope>

    suspend fun liveEvents(): Flow<Unit>

    val currentSource: StateFlow<EventSource>
}

internal class EventGathererImpl(
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider,
    private val eventRepository: EventRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val _currentSource = MutableStateFlow(EventSource.PENDING)

    // TODO: Refactor so currentSource is emitted through the gatherEvents flow, instead of having two separated flows
    override val currentSource: StateFlow<EventSource> get() = _currentSource.asStateFlow()

    private val logger = logger.withFeatureId(SYNC)

    // TODO handle multiple events at once
    override suspend fun gatherEvents(): Flow<EventEnvelope> = eventRepository.observeEvents().flatten()

    override suspend fun liveEvents(): Flow<Unit> = flow {
        _currentSource.value = EventSource.PENDING
        /**
         * Fetches and emits live events based on whether the client supports async notifications.
         * Throws [KaliumSyncException] if event retrieval fails.
         */
        isClientAsyncNotificationsCapableProvider().flatMap { isAsyncNotifications ->
            fetchEventFlow(isAsyncNotifications)
                .onSuccess { emitEvents(it) }
                .onFailure { throw KaliumSyncException("Failure when gathering events", it) }
        }
        // When it ends, reset source back to PENDING
        _currentSource.value = EventSource.PENDING
    }

    /**
     * Retrieves the event flow based on async notification capability.
     */
    private suspend fun fetchEventFlow(isAsyncNotifications: Boolean) = if (isAsyncNotifications) {
        eventRepository.liveEvents()
    } else {
        // in the old system we fetch pending events from the notification stream based on last processed event id
        eventRepository.lastProcessedEventId().flatMap { eventRepository.liveEvents() }
    }

    private suspend fun FlowCollector<Unit>.emitEvents(
        webSocketEventFlow: Flow<WebSocketEvent<Unit>>
    ) = webSocketEventFlow
        .buffer(Channel.UNLIMITED)
        .cancellable()
        .collect { handleWebsocketEvent(it) }

    private suspend fun FlowCollector<Unit>.handleWebsocketEvent(
        webSocketEvent: WebSocketEvent<Unit>
    ) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen(webSocketEvent.shouldProcessPendingEvents)
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived()
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<Unit>) =
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }

    private suspend fun FlowCollector<Unit>.onWebSocketEventReceived() {
        logger.i("Websocket Binary payload received")
        emit(Unit)
    }

    private suspend fun FlowCollector<Unit>.onWebSocketOpen(shouldProcessPendingEvents: Boolean) {
        logger.i("Websocket Open")
        // TODO: Handle time drift in a different way, e.g. the notification api is already called
        //  somewhere else so maybe we can take the time from there ?
//          handleTimeDrift()
        if (shouldProcessPendingEvents) {
            eventRepository
                .fetchEvents()
                .onEach { result ->
                    result.onFailure(::throwPendingEventException)
                }
                .filterIsInstance<Either.Right<EventEnvelope>>()
                .map { offlineEvent -> offlineEvent.value }
                .collect {
                    logger.i("Collecting offline event: ${it.event.toLogString()}")
                    emit(Unit)
                }
            logger.i("Offline events collection finished. Collecting Live events.")
        } else {
            logger.i("Offline events collection skipped due to new system available. Collecting Live events.")
        }
        _currentSource.value = EventSource.LIVE
    }

    private suspend fun handleTimeDrift() {
        eventRepository.fetchServerTime()?.let {
            serverTimeHandler.computeTimeOffset(it.toInstant().epochSeconds)
        }
    }

    private fun throwPendingEventException(failure: CoreFailure) {
        val networkCause = (failure as? NetworkFailure.ServerMiscommunication)?.rootCause
        val isEventNotFound = networkCause is KaliumException.InvalidRequestError
                && networkCause.errorResponse.code == HttpStatusCode.NotFound.value
        throw KaliumSyncException(
            message = "Failure to fetch pending events, aborting Incremental Sync",
            coreFailureCause = if (isEventNotFound) CoreFailure.SyncEventOrClientNotFound else failure
        )
    }
}
