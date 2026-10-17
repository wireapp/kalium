/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageNullableRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.dao.event.NotificationEventStore
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.io.IOException

/** Progress of one WebSocket connection's delivery into the shared Events queue. */
enum class IngestionPhase {
    /** Events are being stored, but this connection has not caught up yet. */
    CATCHING_UP,

    /** Pending events are stored; later events on this connection are live. */
    READY,
}

/**
 * Stores one WebSocket connection's events in the shared Events queue, in server order.
 * A legacy connection pages pending REST notifications once the socket opens and stores frames
 * received meanwhile after them. A consumable connection stores each event before acknowledging
 * it and catches up at its own synchronization marker.
 *
 * Failures that end the connection are thrown as [KaliumSyncException] from the returned flow.
 * The socket connects as [clientId]; pending fetches and acknowledgements use [currentClientId],
 * so a host can stop them when its client is no longer current.
 */
class EventIngestion(
    private val notificationApi: NotificationApi,
    private val eventDAO: EventDAO,
    private val metadataDAO: MetadataDAO,
    private val clientId: String,
    logger: KaliumLogger = kaliumLogger,
    private val currentClientId: suspend () -> Either<CoreFailure, String> = { Either.Right(clientId) },
) {
    private val logger = logger.withTextTag(TAG)

    /**
     * Connects the legacy stream. Collection stops at the caller's discretion; frames that
     * arrive while pending pages are fetched are buffered and stored after those pages.
     */
    suspend fun legacy(clearProcessedOnFirstEvent: Boolean = false): Either<NetworkFailure, Flow<IngestionPhase>> =
        wrapApiRequest { notificationApi.listenToLiveEvents(clientId) }.map { events ->
            flow {
                val connection = Connection(
                    collector = this,
                    marker = null,
                    clearProcessedOnFirstEvent = clearProcessedOnFirstEvent,
                    strict = false,
                    onCaughtUp = {},
                    onMissedNotification = {},
                )
                events
                    .buffer(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
                    .map { it.asConsumable() }
                    .collect { connection.handle(it) }
            }
        }

    /**
     * Connects the consumable stream with this connection's [marker]. [onCaughtUp] runs when the
     * backend confirms the marker; [onMissedNotification] decides how to recover lost events.
     * A [strict] connection fails when an event cannot be stored or acknowledged on the open session,
     * instead of skipping it or opening another session to acknowledge it.
     */
    @Suppress("LongParameterList")
    suspend fun consumable(
        marker: String,
        clearProcessedOnFirstEvent: Boolean = false,
        strict: Boolean = false,
        onCaughtUp: () -> Unit = {},
        onMissedNotification: suspend () -> Unit,
    ): Either<NetworkFailure, Flow<IngestionPhase>> =
        wrapApiRequest { notificationApi.consumeLiveEvents(clientId = clientId, markerId = marker) }.map { events ->
            flow {
                val connection = Connection(this, marker, clearProcessedOnFirstEvent, strict, onCaughtUp, onMissedNotification)
                events.collect { connection.handle(it) }
            }
        }

    /** Pages pending REST notifications from the stored cursor; each page and its cursor commit together. */
    @Suppress("ReturnCount")
    suspend fun fetchPending(): Either<CoreFailure, Unit> {
        val queryClientId = when (val current = currentClientId()) {
            is Either.Left -> return current
            is Either.Right -> current.value
        }
        var hasMore = true
        val storedCursor = wrapStorageNullableRequest { metadataDAO.valueByKey(CURSOR_KEY) }
        var lastFetchedNotificationId = when (storedCursor) {
            is Either.Left -> return storedCursor
            is Either.Right -> storedCursor.value
        }

        while (hasMore) {
            currentCoroutineContext().ensureActive()
            val notificationsPageResult = nextPendingPage(queryClientId, lastFetchedNotificationId)

            if (notificationsPageResult.isSuccessful()) {
                val page = notificationsPageResult.value
                if (page.hasMore && page.notifications.isEmpty()) {
                    return Either.Left(CoreFailure.Unknown(IllegalStateException("Notification page did not advance")))
                }
                val entities = page.notifications.map { event ->
                    NewEventEntity(event.id, event.payload, event.transient, false)
                }
                when (val stored = wrapStorageRequest { eventDAO.storeFetchedPage(entities) }) {
                    is Either.Left -> return stored
                    is Either.Right -> Unit
                }
                hasMore = page.hasMore
                lastFetchedNotificationId = page.notifications.lastOrNull()?.id ?: lastFetchedNotificationId
            } else {
                return Either.Left(NetworkFailure.ServerMiscommunication(notificationsPageResult.kException))
            }
        }
        currentCoroutineContext().ensureActive()
        logger.i("Pending events collection finished. Collecting Live events.")
        return Either.Right(Unit)
    }

    private suspend fun nextPendingPage(queryClientId: String, cursor: String?): NetworkResponse<NotificationResponse> =
        cursor?.let { notificationApi.notificationsByBatch(PAGE_SIZE, queryClientId, it) }
            ?: notificationApi.getAllNotifications(PAGE_SIZE, queryClientId)

    private inner class Connection(
        private val collector: FlowCollector<IngestionPhase>,
        // Present until the backend confirms this connection caught up; legacy streams never have one.
        private var marker: String?,
        private val clearProcessedOnFirstEvent: Boolean,
        private val strict: Boolean,
        private val onCaughtUp: () -> Unit,
        private val onMissedNotification: suspend () -> Unit,
    ) {
        private val isLive get() = marker.isNullOrBlank()
        private var clearOnFirstEvent = false

        suspend fun handle(webSocketEvent: WebSocketEvent<ConsumableNotificationResponse>) {
            when (webSocketEvent) {
                is WebSocketEvent.Open -> opened(webSocketEvent.shouldProcessPendingEvents)
                is WebSocketEvent.NonBinaryPayloadReceived ->
                    throw KaliumSyncException("NonBinaryPayloadReceived", CoreFailure.Unknown(null))
                is WebSocketEvent.Close -> closed(webSocketEvent.cause)
                is WebSocketEvent.BinaryPayloadReceived -> when (val event = webSocketEvent.payload) {
                    is ConsumableNotificationResponse.EventNotification -> received(event.data)
                    ConsumableNotificationResponse.MissedNotification -> {
                        logger.d("Handling ConsumableNotificationResponse.MissedNotification")
                        onMissedNotification()
                    }
                    is ConsumableNotificationResponse.SynchronizationNotification -> synchronized(event)
                }
            }
        }

        private suspend fun opened(isLegacy: Boolean) {
            clearOnFirstEvent = clearProcessedOnFirstEvent
            logger.d("set all unprocessed events as pending")
            wrapStorageRequest { eventDAO.setAllUnprocessedEventsAsPending() }.onFailure(::throwPendingEventException)
            if (isLegacy) {
                logger.d("fetch pending events from server")
                fetchPending().onFailure(::throwPendingEventException)
            }
            // Legacy pending events are stored now; consumable ones arrive before this connection's marker.
            collector.emit(if (isLegacy) IngestionPhase.READY else IngestionPhase.CATCHING_UP)
        }

        private suspend fun received(data: EventDataDTO) {
            data.deliveryTag?.let { logger.d("Handling ConsumableNotificationResponse.EventNotification") }
            val live = isLive
            if (clearOnFirstEvent) {
                clearOnFirstEvent = false
                logger.d("clear processed events before ${data.event.id}")
                clearProcessedEvents(data.event.id)
            }
            val event = data.event
            logger.d("insert event ${event.id} from WS")
            wrapStorageRequest {
                eventDAO.insertEvents(listOf(NewEventEntity(event.id, event.payload, event.transient, live)))
            }.onFailure {
                // Acknowledging later events would let the backend drop this one.
                if (strict) throw KaliumSyncException("Live event storage failed", it)
            }.onSuccess {
                data.deliveryTag?.let { acknowledge(it) }
                if (!event.transient) wrapStorageRequest { metadataDAO.insertValue(event.id, CURSOR_KEY) }
                collector.emit(if (live) IngestionPhase.READY else IngestionPhase.CATCHING_UP)
            }
        }

        private suspend fun synchronized(event: ConsumableNotificationResponse.SynchronizationNotification) {
            logger.d("Handling ConsumableNotificationResponse.SynchronizationNotification")
            event.data.deliveryTag?.let { acknowledge(it) }
            if (event.data.markerId == marker.orEmpty()) {
                logger.d("Handling current sentinel marker [${event.data.markerId}] for this session.")
                marker = null
                onCaughtUp()
                collector.emit(IngestionPhase.READY)
            } else {
                logger.d("Skipping this sentinel marker [${event.data.markerId}] is not valid for this session.")
            }
        }

        private suspend fun acknowledge(deliveryTag: ULong) {
            logger.d("Handling ackEvent")
            val ackClientId = (currentClientId() as? Either.Right)?.value ?: return
            val request = EventAcknowledgeRequest(
                type = AcknowledgeType.ACK,
                data = AcknowledgeData(deliveryTag = deliveryTag, multiple = false)
            )
            if (!strict) {
                notificationApi.acknowledgeEvents(clientId = ackClientId, markerId = marker.orEmpty(), eventAcknowledgeRequest = request)
                return
            }
            val sent = notificationApi.acknowledgeEventsOnOpenSession(ackClientId, marker.orEmpty(), request)
            if (!sent.isSuccessful()) {
                throw KaliumSyncException("Acknowledgement failed", NetworkFailure.ServerMiscommunication(sent.kException))
            }
        }
    }

    @Suppress("ThrowsCount")
    private fun closed(cause: Throwable?): Nothing = when (cause) {
        null -> {
            logger.i("Websocket closed normally, will retry to keep connection alive")
            throw KaliumSyncException("Websocket closed normally", CoreFailure.Unknown(null))
        }
        is IOException -> throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))
        else -> throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
    }

    private suspend fun clearProcessedEvents(eventId: String): Either<CoreFailure, Unit> =
        wrapStorageRequest { eventDAO.getEventById(eventId) }.fold(
            { wrapStorageRequest { eventDAO.deleteAllProcessedEvents() } },
            { entity -> wrapStorageRequest { eventDAO.deleteProcessedEventsBefore(entity.id) } }
        )

    private fun throwPendingEventException(failure: CoreFailure): Nothing {
        val networkCause = (failure as? NetworkFailure.ServerMiscommunication)?.rootCause
        val isEventNotFound = networkCause is KaliumException.InvalidRequestError && networkCause.errorResponse.code == NOT_FOUND
        throw KaliumSyncException(
            message = "Failure to fetch pending events, aborting Incremental Sync",
            coreFailureCause = if (isEventNotFound) CoreFailure.SyncEventOrClientNotFound else failure
        )
    }

    private fun WebSocketEvent<EventResponseToStore>.asConsumable(): WebSocketEvent<ConsumableNotificationResponse> = when (this) {
        is WebSocketEvent.BinaryPayloadReceived ->
            WebSocketEvent.BinaryPayloadReceived(ConsumableNotificationResponse.EventNotification(EventDataDTO(null, payload)))
        is WebSocketEvent.Close -> WebSocketEvent.Close(cause)
        is WebSocketEvent.NonBinaryPayloadReceived -> WebSocketEvent.NonBinaryPayloadReceived(payload)
        is WebSocketEvent.Open -> WebSocketEvent.Open(shouldProcessPendingEvents)
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val NOT_FOUND = 404
        const val CURSOR_KEY = NotificationEventStore.FETCH_CURSOR_KEY
        const val TAG = "[EventIngestion]"
    }
}
