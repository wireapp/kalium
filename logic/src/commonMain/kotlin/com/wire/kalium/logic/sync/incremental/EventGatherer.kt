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
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logger.obfuscateId
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
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

    val currentSource: StateFlow<EventSource>
}

internal class EventGathererImpl(
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider,
    private val eventRepository: EventRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    processingScope: CoroutineScope,
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val _currentSource = MutableStateFlow(EventSource.PENDING)

    // TODO: Refactor so currentSource is emitted through the gatherEvents flow, instead of having two separated flows
    override val currentSource: StateFlow<EventSource> get() = _currentSource.asStateFlow()

    private val offlineEventBuffer = EventProcessingHistory()
    private val asyncIncrementalSyncMetadata = AsyncIncrementalSyncMetadata(processingScope)
    private val logger = logger.withFeatureId(SYNC)

    override suspend fun gatherEvents(): Flow<EventEnvelope> = flow {
        offlineEventBuffer.clear()
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


    private suspend fun FlowCollector<EventEnvelope>.emitEvents(
        webSocketEventFlow: Flow<WebSocketEvent<EventEnvelope>>
    ) = webSocketEventFlow
        .buffer(Channel.UNLIMITED)
        .distinctUntilChanged()
        .cancellable()
        .collect { handleWebsocketEvent(it) }

    private suspend fun FlowCollector<EventEnvelope>.handleWebsocketEvent(
        webSocketEvent: WebSocketEvent<EventEnvelope>
    ) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen(webSocketEvent.shouldProcessPendingEvents)
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private suspend fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<EventEnvelope>) =
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }.also {
            _currentSource.value = EventSource.PENDING
            asyncIncrementalSyncMetadata.clear()
        }

    private suspend fun FlowCollector<EventEnvelope>.onWebSocketEventReceived(
        webSocketEvent: WebSocketEvent.BinaryPayloadReceived<EventEnvelope>
    ) {
        val envelope = webSocketEvent.payload
        val obfuscatedId = envelope.event.id.obfuscateId()
        asyncIncrementalSyncMetadata.scheduleNewCatchingUpJob { _currentSource.value = EventSource.LIVE }
        logger.i("Websocket Received payload: ${envelope.event.toLogString()}")
        if (offlineEventBuffer.contains(envelope.event)) {
            if (offlineEventBuffer.clearHistoryIfLastEventEquals(envelope.event)) {
                // Really live
                logger.d("Removed most recent event from offlineEventBuffer: '$obfuscatedId'")
            } else {
                // Really live
                logger.d("Removing event from offlineEventBuffer: $obfuscatedId")
                offlineEventBuffer.remove(envelope.event)
            }
            logger
                .d("Skipping emit of event from WebSocket because already emitted as offline event $obfuscatedId")
        } else {
            logger.d("Event never seen before $obfuscatedId - We are live")
            emit(envelope)
        }
    }

    private suspend fun FlowCollector<EventEnvelope>.onWebSocketOpen(shouldProcessPendingEvents: Boolean) {
        logger.i("Websocket Open")
        // TODO: Handle time drift in a different way, e.g. the notification api is already called
        //  somewhere else so maybe we can take the time from there ?
//          handleTimeDrift()
        if (shouldProcessPendingEvents) {
            eventRepository
                .pendingEvents()
                .onEach { result ->
                    result.onFailure(::throwPendingEventException)
                }
                .filterIsInstance<Either.Right<EventEnvelope>>()
                .map { offlineEvent -> offlineEvent.value }
                .collect {
                    logger.i("Collecting offline event: ${it.event.toLogString()}")
                    offlineEventBuffer.add(it.event)
                    emit(it)
                }
            logger.i("Offline events collection finished. Collecting Live events.")
            _currentSource.value = EventSource.LIVE
        } else {
            asyncIncrementalSyncMetadata.createNewCatchingUpJob { _currentSource.value = EventSource.LIVE }
            logger.i("Offline events collection skipped due to new system available. Catching up now: $asyncIncrementalSyncMetadata")
        }
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
