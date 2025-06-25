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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatten
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.mapLeft
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.IsClientAsyncNotificationsCapableProvider
import com.wire.kalium.logic.data.event.EventEnvelope
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.event.PendingEventInfo
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.util.ServerTimeHandler
import com.wire.kalium.logic.util.ServerTimeHandlerImpl
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.errors.IOException
import io.mockative.Mockable
import kotlinx.coroutines.CoroutineScope
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
     * Emits all unprocessed events stored locally, and updates the [currentSource] depending on:
     * - Whether async notifications are enabled,
     * - Whether pending events are still being processed.
     *
     * When `isAsyncNotifications = true`, this flow emits only live events.
     * When `false`, this flow starts with pending events and switches to live once completed.
     *
     * This flow is hot and updated based on the internal event trigger mechanism.
     */
    suspend fun gatherEvents(): Flow<EventEnvelope>

    /**
     * Establishes a WebSocket connection to start receiving real-time events.
     *
     * Depending on the server configuration and async notification support, this function may:
     * - Trigger the reception of pending events if applicable,
     * - Initiate event acknowledgment flows,
     * - Start the transition to [EventSource.LIVE].
     *
     * Emits `Unit` whenever a new event frame arrives.
     */
    suspend fun liveEvents(): Flow<Unit>

    /**
     * Represents the current source of events:
     * - [EventSource.PENDING] if the system is processing past events,
     * - [EventSource.LIVE] if only real-time events are being received.
     *
     * This state is managed internally and exposed as a [StateFlow] for UI or logic to react.
     */
    val currentSource: StateFlow<EventSource>
}

internal class EventGathererImpl(
    private val isClientAsyncNotificationsCapableProvider: IsClientAsyncNotificationsCapableProvider,
    private val eventRepository: EventRepository,
    private val serverTimeHandler: ServerTimeHandler = ServerTimeHandlerImpl(),
    private val processingScope: CoroutineScope,
    private val liveSourceChangeHandler: LiveSourceChangeHandler = LiveSourceChangeHandlerImpl(processingScope),
    logger: KaliumLogger = kaliumLogger,
) : EventGatherer {

    private val _currentSource = MutableStateFlow(EventSource.PENDING)

    private val _hasPendingEventsFinished = MutableStateFlow(false)

    // TODO: Refactor so currentSource is emitted through the gatherEvents flow, instead of having two separated flows
    override val currentSource: StateFlow<EventSource> get() = _currentSource.asStateFlow()

    private val logger = logger.withFeatureId(SYNC)

    // TODO handle multiple events at once
    override suspend fun gatherEvents(): Flow<EventEnvelope> {
        val isAsyncNotifications = isClientAsyncNotificationsCapableProvider().getOrElse { false }

        return eventRepository.observeEvents()
            .onEach { events ->
                if (!isAsyncNotifications) {
                    val hasPendingFinished = _hasPendingEventsFinished.value
                    val isPending = events.any { !it.deliveryInfo.isLive }

                    when {
                        hasPendingFinished && events.size <= MIN_PENDING_EVENTS_TO_SWITCH_TO_LIVE -> {
                            _currentSource.value = EventSource.LIVE
                        }

                        isPending -> {
                            kaliumLogger.d("$TAG source switch to PENDING")
                            _currentSource.value = EventSource.PENDING
                        }

                        else -> {
                            kaliumLogger.d("$TAG source switch to LIVE")
                            _currentSource.value = EventSource.LIVE
                        }
                    }
                }
            }
            .map { events ->
                val hasPendingFinished = _hasPendingEventsFinished.value

                events.filter { event ->
                    if (!isAsyncNotifications && !hasPendingFinished && event.deliveryInfo.isLive) {
                        kaliumLogger.d("$TAG skipping live event ${event.event.id.obfuscateId()} before pending")
                        false
                    } else {
                        true
                    }
                }
            }
            .flatten()
    }

    override suspend fun liveEvents(): Flow<Unit> = flow {
        /**
         * Fetches and emits live events based on whether the client supports async notifications.
         * Throws [KaliumSyncException] if event retrieval fails.
         */
        isClientAsyncNotificationsCapableProvider().flatMap { isAsyncNotifications ->
            fetchEventFlow(isAsyncNotifications)
                .onSuccess { emitEvents(it) }
                .onFailure { throw KaliumSyncException("Failure when gathering events", it) }
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
        webSocketEventFlow: Flow<WebSocketEvent<Boolean>>
    ) = webSocketEventFlow
        .buffer(Channel.UNLIMITED)
        .cancellable()
        .collect { handleWebsocketEvent(it) }

    private suspend fun FlowCollector<Unit>.handleWebsocketEvent(
        webSocketEvent: WebSocketEvent<Boolean>
    ) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen(webSocketEvent.shouldProcessPendingEvents)
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private suspend fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<Boolean>) {
        val isAsync = webSocketEvent.payload ?: false
        if (isAsync) {
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

    private suspend fun FlowCollector<Unit>.onWebSocketEventReceived(webSocketEvent: WebSocketEvent.BinaryPayloadReceived<Boolean>) {
        val isAsync: Boolean = webSocketEvent.payload
        if (isAsync) {
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
        if (shouldProcessPendingEvents) {
            eventRepository
                .fetchEvents()
                .onEach { result ->
                    result.onFailure(::throwPendingEventException)
                }
                .filterIsInstance<Either.Right<PendingEventInfo>>()
                .map { it.value }
                .collect {
                    if (!it.hasMore) {
                        kaliumLogger.d("$TAG no more pending events")
                        _hasPendingEventsFinished.value = true
                    }
                    emit(Unit)
                }
            logger.i("Offline events collection finished. Collecting Live events.")
        } else {
            logger.i("Offline events collection skipped due to new system available. Collecting Live events.")
            liveSourceChangeHandler.startNewCatchingUpJob(onStartIntervalReached = { _currentSource.value = EventSource.LIVE })
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
            message = "$TAG Failure to fetch pending events, aborting Incremental Sync",
            coreFailureCause = if (isEventNotFound) CoreFailure.SyncEventOrClientNotFound else failure
        )
    }

    companion object {
        const val MIN_PENDING_EVENTS_TO_SWITCH_TO_LIVE = 5
        const val TAG = "[EventGatherer]"
    }
}
