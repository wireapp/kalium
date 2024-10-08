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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile
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
     * Will stop or keep gathering accordingly to the current [ConnectionPolicy]
     */
    suspend fun gatherEvents(): Flow<EventEnvelope>

    val currentSource: StateFlow<EventSource>
}

internal class EventGathererImpl(
    private val eventRepository: EventRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val _currentSource = MutableStateFlow(EventSource.PENDING)
    // TODO: Refactor so currentSource is emitted through the gatherEvents flow, instead of having two separated flows
    override val currentSource: StateFlow<EventSource> get() = _currentSource.asStateFlow()

    private val offlineEventBuffer = EventProcessingHistory()
    private val logger = logger.withFeatureId(SYNC)

    override suspend fun gatherEvents(): Flow<EventEnvelope> = flow {
        offlineEventBuffer.clear()
        _currentSource.value = EventSource.PENDING
        eventRepository.lastProcessedEventId().flatMap {
            eventRepository.liveEvents()
        }.onSuccess { webSocketEventFlow ->
            handleWebSocketEventsWhilePolicyAllows(webSocketEventFlow)
        }.onFailure {
            // throw so it is handled by coroutineExceptionHandler
            throw KaliumSyncException("Failure when gathering events", it)
        }
        // When it ends, reset source back to PENDING
        _currentSource.value = EventSource.PENDING
    }

    private suspend fun FlowCollector<EventEnvelope>.handleWebSocketEventsWhilePolicyAllows(
        webSocketEventFlow: Flow<WebSocketEvent<EventEnvelope>>
    ) = webSocketEventFlow.combine(incrementalSyncRepository.connectionPolicyState)
        .buffer(Channel.UNLIMITED)
        .transformWhile { (webSocketEvent, policy) ->
            val isKeepAlivePolicy = policy == ConnectionPolicy.KEEP_ALIVE
            val isOpenEvent = webSocketEvent is WebSocketEvent.Open
            if (isKeepAlivePolicy || isOpenEvent) {
                // Emit if keeping alive, always emit if is an Open event
                emit(webSocketEvent)
            }
            // Only continue collecting if the Policy allows it
            isKeepAlivePolicy
        }
        // Prevent repetition of events, in case the policy changed
        .distinctUntilChanged()
        .cancellable()
        .collect { handleWebsocketEvent(it) }

    private suspend fun FlowCollector<EventEnvelope>.handleWebsocketEvent(
        webSocketEvent: WebSocketEvent<EventEnvelope>
    ) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen()
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<EventEnvelope>) =
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }

    private suspend fun FlowCollector<EventEnvelope>.onWebSocketEventReceived(
        webSocketEvent: WebSocketEvent.BinaryPayloadReceived<EventEnvelope>
    ) {
        logger.i("Websocket Received binary payload")
        val envelope = webSocketEvent.payload
        val obfuscatedId = envelope.event.id.obfuscateId()
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

    private suspend fun FlowCollector<EventEnvelope>.onWebSocketOpen() {
        logger.i("Websocket Open")
        handleTimeDrift()
        eventRepository
            .pendingEvents()
            .onEach { result ->
                result.onFailure(::throwPendingEventException)
            }
            .filterIsInstance<Either.Right<EventEnvelope>>()
            .map { offlineEvent -> offlineEvent.value }
            .collect {
                logger.i("Collecting offline event: ${it.event.id.obfuscateId()}")
                offlineEventBuffer.add(it.event)
                emit(it)
            }
        logger.i("Offline events collection finished. Collecting Live events.")
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
