package com.wire.kalium.logic.sync.incremental

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.SYNC
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.ConnectionPolicy
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.combine
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.notification.WebSocketEvent
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformWhile

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
    suspend fun gatherEvents(): Flow<Event>

    val currentSource: StateFlow<EventSource>
}

internal class EventGathererImpl(
    private val eventRepository: EventRepository,
    private val incrementalSyncRepository: IncrementalSyncRepository
) : EventGatherer {

    private val _currentSource = MutableStateFlow(EventSource.PENDING)
    override val currentSource: StateFlow<EventSource> get() = _currentSource.asStateFlow()

    private val offlineEventBuffer = PendingEventsBuffer()
    private val logger = kaliumLogger.withFeatureId(SYNC)

    override suspend fun gatherEvents(): Flow<Event> = flow {
        offlineEventBuffer.clear()
        _currentSource.value = EventSource.PENDING
        eventRepository.lastEventId().map { eventId ->
            eventRepository.updateLastProcessedEventId(eventId)
        }.flatMap {
            eventRepository.liveEvents()
        }.onSuccess { webSocketEventFlow ->
            handleWebSocketEventsWhilePolicyAllows(webSocketEventFlow)
        }.onFailure {
            // throw so it is handled by coroutineExceptionHandler
            throw KaliumSyncException("Failure when gathering events", it)
        }
    }

    private suspend fun FlowCollector<Event>.handleWebSocketEventsWhilePolicyAllows(
        webSocketEventFlow: Flow<WebSocketEvent<Event>>
    ) = webSocketEventFlow.combine(incrementalSyncRepository.connectionPolicyState)
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

    private suspend fun FlowCollector<Event>.handleWebsocketEvent(webSocketEvent: WebSocketEvent<Event>) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen()
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> handleWebSocketClosure(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> logger.w("Non binary event received on Websocket")
    }

    private fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<Event>) =
        when (val cause = webSocketEvent.cause) {
            null -> logger.i("Websocket closed normally")
            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))
            else ->
                throw KaliumSyncException("Unknown Websocket error", CoreFailure.Unknown(cause))
        }

    private suspend fun FlowCollector<Event>.onWebSocketEventReceived(webSocketEvent: WebSocketEvent.BinaryPayloadReceived<Event>) {
        logger.i("Websocket Received binary payload")
        val event = webSocketEvent.payload
        if (offlineEventBuffer.contains(event)) {
            if (offlineEventBuffer.clearBufferIfLastEventEquals(event)) {
                // Really live
                logger.d("Removed most recent event from offlineEventBuffer: '${event.id.obfuscateId()}'")
            } else {
                // Really live
                logger.d("Removing event from offlineEventBuffer: ${event.id.obfuscateId()}")
                offlineEventBuffer.remove(event)
            }
            logger
                .d("Skipping emit of event from WebSocket because already emitted as offline event ${event.id.obfuscateId()}")
        } else {
            logger.d("Event never seen before ${event.id.obfuscateId()} - We are live")
            emit(event)
        }
    }

    private suspend fun FlowCollector<Event>.onWebSocketOpen() {
        logger.i("Websocket Open")
        eventRepository
            .pendingEvents()
            .onEach {
                it.onFailure { failure ->
                    throw KaliumSyncException("Failure to fetch pending events, aborting Incremental Sync", failure)
                }
            }.filterIsInstance<Either.Right<Event>>()
            .map { offlineEvent ->
                offlineEvent.value
            }.collect {
                logger.i("Collecting offline event: ${it.id.obfuscateId()}")
                offlineEventBuffer.add(it)
                emit(it)
            }
        logger.i("Offline events collection finished. Collecting Live events.")
        _currentSource.value = EventSource.LIVE
    }
}
