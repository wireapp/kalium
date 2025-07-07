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

package com.wire.kalium.logic.data.event

import co.touchlab.stately.concurrency.AtomicReference
import com.benasher44.uuid.uuid4
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import io.ktor.http.HttpStatusCode
import io.mockative.Mockable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

@Mockable
interface EventRepository {

    /**
     * Performs an acknowledgment of the missed event after performing a slow sync.
     */
    suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit>
    suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<EventVersion>>>
    suspend fun setEventAsProcessed(eventId: String): Either<StorageFailure, Unit>

    /**
     * Parse events from an external JSON payload
     *
     * @return List of [EventEnvelope]
     */
    fun parseExternalEvents(data: String): List<EventEnvelope>

    /**
     * Retrieves the last saved event ID from the storage.
     *
     * @return an [Either] object representing either a [StorageFailure] or a [String].
     *         - If the retrieval is successful, returns [Either.Right] with the last saved event ID as a [String].
     *         - If there is a failure during retrieval, returns [Either.Left] with a [StorageFailure] object.
     */
    suspend fun lastSavedEventId(): Either<StorageFailure, String>

    /**
     * Clears the last saved event ID.
     *
     * @return An [Either] object representing the result of the operation.
     * The [Either] object contains either a [StorageFailure] if the operation fails, or [Unit] if the operation succeeds.
     */
    suspend fun clearLastSavedEventId(): Either<StorageFailure, Unit>

    /**
     * Updates the last saved event ID.
     *
     * @param eventId The ID of the event to be set as the last saved event ID.
     *
     * @return An [Either] object representing the result of the operation.
     * The [Either] object contains either a [StorageFailure] if the operation fails, or [Unit] if the operation succeeds.
     */
    suspend fun updateLastSavedEventId(eventId: String): Either<StorageFailure, Unit>

    suspend fun fetchMostRecentEventId(): Either<CoreFailure, String>

    /**
     * Fetches the oldest available event ID from remote.
     *
     * @return Either containing a [CoreFailure] or the oldest available event ID as a String.
     */
    suspend fun fetchOldestAvailableEventId(): Either<CoreFailure, String>
    suspend fun fetchServerTime(): String?
    suspend fun observeEvents(): Flow<List<EventEnvelope>>
}

@Suppress("TooManyFunctions", "LongParameterList")
class EventDataSource(
    private val notificationApi: NotificationApi,
    private val metadataDAO: MetadataDAO,
    private val eventDAO: EventDAO,
    private val currentClientId: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
) : EventRepository {

    private val clearOnFirstWSMessage = MutableStateFlow(false)
    private val sentinelMarker = AtomicReference<SentinelMarker>(SentinelMarker.None)

    override suspend fun observeEvents(): Flow<List<EventEnvelope>> {
        var lastEmittedEventId: String? = null
        return eventDAO.observeUnprocessedEvents().transform { eventEntities ->
            kaliumLogger.d("$TAG got ${eventEntities.size} unprocessed events")
            kaliumLogger.d("$TAG current last emitted event id: ${lastEmittedEventId?.obfuscateId()}")

            val emittedEventIndex = eventEntities.indexOfFirst { entity -> entity.eventId == lastEmittedEventId }

            if (emittedEventIndex == -1) {
                emit(eventEntities)
                return@transform
            }
            if (emittedEventIndex != eventEntities.lastIndex) {
                kaliumLogger.d("$TAG filtered out ${emittedEventIndex + 1} events already marked as processed")
                emit(eventEntities.subList(emittedEventIndex + 1, eventEntities.lastIndex))
            } else {
                kaliumLogger.d("$TAG no unprocessed events found")
                emit(emptyList())
            }
        }
            .onEach { entities ->
                entities.lastOrNull()?.let {
                    lastEmittedEventId = it.eventId
                }
            }
            .map { eventEntities ->
                eventEntities.map { entity ->
                    val payload = KtxSerializer.json.decodeFromString<EventResponse>(entity.payload)
                    eventMapper.fromDTO(payload, isLive = entity.isLive)
                }
                    .flatten()
            }
    }

    override suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit> =
        currentClientId().fold(
            { it.left() },
            {
                notificationApi.acknowledgeEvents(it.value, sentinelMarker.get().getMarker(), EventMapper.FULL_ACKNOWLEDGE_REQUEST)
                Unit.right()
            }
        )

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)
    private suspend fun fetchEvents(): Either<CoreFailure, Unit> =
        currentClientId().fold({ Either.Left(it) }, { clientId -> fetchPendingEvents(clientId) })

    override suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<EventVersion>>> =
        currentClientId().flatMap { clientId ->
            val hasConsumableNotifications = clientRegistrationStorage.observeHasConsumableNotifications().firstOrNull()
            if (hasConsumableNotifications == true) {
                consumeLiveEventsFlow(clientId)
            } else {
                liveEventsFlow(clientId)
            }
        }

    private suspend fun consumeLiveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<EventVersion>>> {
        sentinelMarker.set(SentinelMarker.Marker(uuid4().toString()))
        return wrapApiRequest {
            notificationApi.consumeLiveEvents(clientId = clientId.value, markerId = sentinelMarker.get().getMarker())
        }.map { webSocketEventFlow ->
            flow {
                webSocketEventFlow.collect(handleEvents(this, EventVersion.ASYNC))
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun handleEvents(
        flowCollector: FlowCollector<WebSocketEvent<EventVersion>>,
        eventVersion: EventVersion
    ): suspend (value: WebSocketEvent<ConsumableNotificationResponse>) -> Unit =
        { webSocketEvent ->
            when (webSocketEvent) {
                is WebSocketEvent.Open -> {
                    clearOnFirstWSMessage.emit(true)
                    kaliumLogger.d("$TAG set all unprocessed events as pending")
                    setAllUnprocessedEventsAsPending()
                    if (webSocketEvent.shouldProcessPendingEvents) {
                        kaliumLogger.d("$TAG fetch pending events from server")
                        val result = fetchEvents()
                        result.onFailure(::throwPendingEventException)
                    }
                    flowCollector.emit(WebSocketEvent.Open(shouldProcessPendingEvents = webSocketEvent.shouldProcessPendingEvents))
                }

                is WebSocketEvent.NonBinaryPayloadReceived -> {
                    flowCollector.emit(WebSocketEvent.NonBinaryPayloadReceived(webSocketEvent.payload))
                }

                is WebSocketEvent.Close -> {
                    flowCollector.emit(WebSocketEvent.Close(webSocketEvent.cause, eventVersion))
                }

                is WebSocketEvent.BinaryPayloadReceived -> {
                    when (val event: ConsumableNotificationResponse = webSocketEvent.payload) {
                        is ConsumableNotificationResponse.EventNotification -> {
                            if (clearOnFirstWSMessage.value) {
                                clearOnFirstWSMessage.emit(false)
                                kaliumLogger.d("$TAG clear processed events before ${event.data.event.id.obfuscateId()}")
                                clearProcessedEvents(event.data.event.id)
                            }
                            event.data.event.let { eventResponse ->
                                kaliumLogger.d("$TAG insert event ${eventResponse.id.obfuscateId()} from WS")
                                wrapStorageRequest {
                                    eventDAO.insertEvents(
                                        listOf(
                                            NewEventEntity(
                                                eventId = eventResponse.id,
                                                payload = KtxSerializer.json.encodeToString(eventResponse),
                                                isLive = isWebsocketEventReceivedLive()
                                            )
                                        )
                                    )
                                }.onSuccess {
                                    event.data.deliveryTag?.let {
                                        ackEvent(it)
                                    }
                                    if (!event.data.event.transient) {
                                        updateLastSavedEventId(event.data.event.id)
                                    }
                                    flowCollector.emit(WebSocketEvent.BinaryPayloadReceived(eventVersion))
                                }
                            }
                        }

                        ConsumableNotificationResponse.MissedNotification -> {
                            wrapStorageRequest {
                                val eventId = uuid4().toString()
                                eventDAO.insertEvents(
                                    listOf(
                                        NewEventEntity(
                                            eventId = eventId,
                                            payload = KtxSerializer.json.encodeToString(
                                                EventResponse(
                                                    eventId,
                                                    payload = listOf(EventContentDTO.AsyncMissedNotification)
                                                )
                                            ),
                                            isLive = true
                                        )
                                    )
                                )
                            }
                        }

                        is ConsumableNotificationResponse.SynchronizationNotification -> {
                            event.data.deliveryTag?.let { ackEvent(it) }
                            val currentMarker = sentinelMarker.get().getMarker()
                            if (event.data.markerId == currentMarker) {
                                kaliumLogger.d("$TAG Handling current marker [${event.data.markerId}] for this session.")
                                sentinelMarker.set(SentinelMarker.None)
                            } else {
                                kaliumLogger.d("$TAG Skipping this marker [${event.data.markerId}] is not valid for this session.")
                            }
                        }
                    }
                }
            }
        }

    /**
     * Returns true if the current event is received when there is no current marker present for the session.
     */
    private fun isWebsocketEventReceivedLive() = sentinelMarker.get().getMarker().isBlank()

    private suspend fun ackEvent(deliveryTag: ULong): Either<CoreFailure, Unit> {
        return currentClientId().fold(
            { it.left() },
            { clientId ->
                notificationApi.acknowledgeEvents(
                    clientId = clientId.value,
                    markerId = sentinelMarker.get().getMarker(),
                    eventAcknowledgeRequest = EventAcknowledgeRequest(
                        type = AcknowledgeType.ACK,
                        data = AcknowledgeData(
                            deliveryTag = deliveryTag,
                            multiple = false // TODO when use multiple?
                        )
                    )
                )
                Unit.right()
            }
        )
    }

    private suspend fun clearProcessedEvents(eventId: String): Either<StorageFailure, Unit> {
        return wrapStorageRequest {
            eventDAO.getEventById(eventId)
        }
            .fold({
                wrapStorageRequest {
                    eventDAO.deleteAllProcessedEvents()
                }
            }) { eventEntity ->
                wrapStorageRequest {
                    eventDAO.deleteProcessedEventsBefore(eventEntity.id)
                }
            }
    }

    private suspend fun setAllUnprocessedEventsAsPending(): Either<CoreFailure, Unit> = wrapStorageRequest {
        eventDAO.setAllUnprocessedEventsAsPending()
    }

    private suspend fun liveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<EventVersion>>> =
        wrapApiRequest { notificationApi.listenToLiveEvents(clientId.value) }
            .map { webSocketEventFlow ->
                flow {
                    webSocketEventFlow
                        .buffer(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
                        .map {
                            when (it) {
                                is WebSocketEvent.BinaryPayloadReceived<EventResponse> ->
                                    WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>(
                                        ConsumableNotificationResponse.EventNotification(
                                            EventDataDTO(
                                                null,
                                                it.payload
                                            )
                                        )
                                    )

                                is WebSocketEvent.Close<EventResponse> -> WebSocketEvent.Close(it.cause)
                                is WebSocketEvent.NonBinaryPayloadReceived<EventResponse> ->
                                    WebSocketEvent.NonBinaryPayloadReceived(it.payload)

                                is WebSocketEvent.Open<EventResponse> -> WebSocketEvent.Open(it.shouldProcessPendingEvents)
                            }
                        }
                        .collect(handleEvents(this, EventVersion.LEGACY))
                }
            }

    private suspend fun fetchPendingEvents(clientId: ClientId): Either<CoreFailure, Unit> {
        var hasMore = true
        var lastFetchedNotificationId = metadataDAO.valueByKey(LAST_SAVED_EVENT_ID_KEY)

        while (coroutineContext.isActive && hasMore) {
            val notificationsPageResult = getNextPendingEventsPage(lastFetchedNotificationId, clientId)

            if (notificationsPageResult.isSuccessful()) {
                hasMore = notificationsPageResult.value.hasMore
                lastFetchedNotificationId = notificationsPageResult.value.notifications.lastOrNull()?.id

                val entities = notificationsPageResult.value.notifications.mapNotNull { event ->
                    event.payload?.let {
                        NewEventEntity(
                            eventId = event.id,
                            payload = KtxSerializer.json.encodeToString(event),
                            isLive = false
                        )
                    }
                }
                kaliumLogger.d("$TAG inserting ${entities.size} events from pending notifications, hasMore: $hasMore")
                val eventIdsToRemove = entities.map { it.eventId }
                wrapStorageRequest {
                    eventDAO.deleteUnprocessedLiveEventsByIds(eventIdsToRemove)
                }.onSuccess {
                    wrapStorageRequest {
                        eventDAO.insertEvents(entities)
                    }.onSuccess {
                        notificationsPageResult.value.notifications.lastOrNull { !it.transient }?.let {
                            updateLastSavedEventId(it.id)
                        }
                    }
                }
            } else {
                return Either.Left(NetworkFailure.ServerMiscommunication(notificationsPageResult.kException))
            }
        }
        kaliumLogger.i("Pending events collection finished. Collecting Live events.")
        return Either.Right(Unit)
    }

    override fun parseExternalEvents(data: String): List<EventEnvelope> {
        val notificationResponse = Json.decodeFromString<NotificationResponse>(data)
        return notificationResponse.notifications.flatMap {
            eventMapper.fromDTO(it, isLive = false)
        }
    }

    override suspend fun lastSavedEventId(): Either<StorageFailure, String> = wrapStorageRequest {
        metadataDAO.valueByKey(LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun clearLastSavedEventId(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.deleteValue(LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun fetchMostRecentEventId(): Either<CoreFailure, String> =
        currentClientId()
            .flatMap { currentClientId ->
                wrapApiRequest { notificationApi.mostRecentNotification(currentClientId.value) }
                    .map { it.id }
            }

    override suspend fun updateLastSavedEventId(eventId: String): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.insertValue(eventId, LAST_SAVED_EVENT_ID_KEY)
    }

    override suspend fun setEventAsProcessed(eventId: String): Either<StorageFailure, Unit> = wrapStorageRequest {
        eventDAO.markEventAsProcessed(eventId)
    }

    private suspend fun getNextPendingEventsPage(
        lastFetchedNotificationId: String?,
        clientId: ClientId
    ): NetworkResponse<NotificationResponse> = lastFetchedNotificationId?.let {
        notificationApi.notificationsByBatch(NOTIFICATIONS_QUERY_SIZE, clientId.value, it)
    } ?: notificationApi.getAllNotifications(NOTIFICATIONS_QUERY_SIZE, clientId.value)

    override suspend fun fetchOldestAvailableEventId(): Either<CoreFailure, String> =
        currentClientId().flatMap { clientId ->
            wrapApiRequest {
                notificationApi.oldestNotification(clientId.value)
            }
        }.map { it.id }

    override suspend fun fetchServerTime(): String? {
        val result = notificationApi.getServerTime(NOTIFICATIONS_QUERY_SIZE)
        return if (result.isSuccessful()) {
            result.value
        } else {
            null
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

    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 100
        const val LAST_SAVED_EVENT_ID_KEY = "last_processed_event_id"
        const val TAG = "[EventRepository]"
    }
}
