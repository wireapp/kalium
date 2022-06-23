package com.wire.kalium.logic.sync

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventRepository
import com.wire.kalium.logic.data.sync.SyncRepository
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.network.api.notification.WebSocketEvent
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull

interface EventGatherer {
    suspend fun gatherEvents(): Flow<Event>
}

internal class EventGathererImpl(
    private val eventRepository: EventRepository,
    private val syncRepository: SyncRepository
) : EventGatherer {

    private val offlineEventBuffer = PendingEventsBuffer()

    override suspend fun gatherEvents(): Flow<Event> = flow {
        offlineEventBuffer.clear()
        eventRepository.lastEventId().map { eventId ->
            eventRepository.updateLastProcessedEventId(eventId)
        }.flatMap {
            eventRepository.liveEvents()
        }.onSuccess { webSocketEventFlow ->
            webSocketEventFlow.collect { webSocketEvent ->
                handleWebsocketEvent(webSocketEvent)
            }
        }.onFailure {
            // throw so it is handled by coroutineExceptionHandler
            throw KaliumSyncException("Failure when gathering events", it)
        }
    }

    private suspend fun FlowCollector<Event>.handleWebsocketEvent(webSocketEvent: WebSocketEvent<Event>) = when (webSocketEvent) {
        is WebSocketEvent.Open -> onWebSocketOpen()
        is WebSocketEvent.BinaryPayloadReceived -> onWebSocketEventReceived(webSocketEvent)
        is WebSocketEvent.Close -> throwOnWebSocketClosed(webSocketEvent)
        is WebSocketEvent.NonBinaryPayloadReceived -> kaliumLogger.w("Non binary event received on Websocket")
    }

    private fun throwOnWebSocketClosed(webSocketEvent: WebSocketEvent.Close<Event>): Nothing =
        throw when (val cause = webSocketEvent.cause) {
            is IOException ->
                KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            is Throwable ->
                KaliumSyncException("Unknown Websocket error", CoreFailure.Unknown(cause))

            else -> KaliumSyncException(
                "Websocket event collecting stopped",
                NetworkFailure.NoNetworkConnection(null)
            )
        }

    private suspend fun FlowCollector<Event>.onWebSocketEventReceived(webSocketEvent: WebSocketEvent.BinaryPayloadReceived<Event>) {
        kaliumLogger.i("SYNC: Websocket Received binary payload")
        val event = webSocketEvent.payload
        if (offlineEventBuffer.contains(event)) {
            if (offlineEventBuffer.clearBufferIfLastEventEquals(event)) {
                // Really live
                kaliumLogger.d("SYNC: Removed most recent event from offlineEventBuffer: '${event.id}'")
            } else {
                // Really live
                kaliumLogger.d("SYNC: Removing event from offlineEventBuffer: ${event.id}")
                offlineEventBuffer.remove(event)
            }
            kaliumLogger.d("SYNC: Skipping emit of event from WebSocket because already emitted as offline event ${event.id}")
        } else {
            kaliumLogger.d("SYNC: Event never seen before ${event.id} - We are live")
            emit(event)
        }
    }

    private suspend fun FlowCollector<Event>.onWebSocketOpen() {
        kaliumLogger.i("SYNC: Websocket Open")
        eventRepository
            .pendingEvents()
            .mapNotNull { offlineEventOrFailure ->
                when (offlineEventOrFailure) {
                    is Either.Left -> null
                    is Either.Right -> offlineEventOrFailure.value
                }
            }
            .collect {
                kaliumLogger.i("SYNC: Collecting offline event: ${it.id}")
                offlineEventBuffer.add(it)
                emit(it)
            }
        kaliumLogger.i("SYNC: Offline events collection finished")
        syncRepository.updateSyncState { SyncState.Live }
    }
}
