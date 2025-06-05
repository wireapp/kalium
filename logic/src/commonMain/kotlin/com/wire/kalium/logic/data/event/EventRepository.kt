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
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
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
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

interface EventRepository {

    /**
     * Performs an acknowledgment of the missed event after performing a slow sync.
     */
    suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit>
    suspend fun fetchEvents(): Flow<Either<CoreFailure, EventEnvelope>>
    suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<Unit>>>
    suspend fun updateLastProcessedEventId(eventId: String): Either<StorageFailure, Unit>

    /**
     * Parse events from an external JSON payload
     *
     * @return List of [EventEnvelope]
     */
    fun parseExternalEvents(data: String): List<EventEnvelope>

    /**
     * Retrieves the last processed event ID from the storage.
     *
     * @return an [Either] object representing either a [StorageFailure] or a [String].
     *         - If the retrieval is successful, returns [Either.Right] with the last processed event ID as a [String].
     *         - If there is a failure during retrieval, returns [Either.Left] with a [StorageFailure] object.
     */
    suspend fun lastProcessedEventId(): Either<StorageFailure, String>

    /**
     * Clears the last processed event ID.
     *
     * @return An [Either] object representing the result of the operation.
     * The [Either] object contains either a [StorageFailure] if the operation fails, or [Unit] if the operation succeeds.
     */
    suspend fun clearLastProcessedEventId(): Either<StorageFailure, Unit>

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
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId)
) : EventRepository {

    private val clearOnFirstWSMessage = MutableStateFlow(false)

    override suspend fun observeEvents(): Flow<List<EventEnvelope>> = flow {
        eventDAO.observeUnprocessedEvents()
            .conflate()
            // add limit of 200, think about making it dynamic depending on device ram
            // control flow of by websocket new push concat map?
            .distinctUntilChanged()
            .collect { batch ->
                val events = batch.map { entity ->
                    KtxSerializer.json.decodeFromString<EventResponse>(entity.payload)
                }
                events.forEach {
                    emit(eventMapper.fromDTO(it, isLive = false))
                }
            }
    }

    override suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit> =
        currentClientId().fold(
            { it.left() },
            {
                // todo(ym) check for errors.
                notificationApi.acknowledgeEvents(it.value, EventMapper.FULL_ACKNOWLEDGE_REQUEST)
                Unit.right()
            }
        )

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)
    override suspend fun fetchEvents(): Flow<Either<CoreFailure, EventEnvelope>> =
        currentClientId().fold({ flowOf(Either.Left(it)) }, { clientId -> pendingEventsFlow(clientId) })

    override suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<Unit>>> =
        currentClientId().flatMap { clientId ->
            val hasConsumableNotifications = clientRegistrationStorage.observeHasConsumableNotifications().firstOrNull()
            if (hasConsumableNotifications == true) {
                consumeLiveEventsFlow(clientId)
            } else {
                liveEventsFlow(clientId)
            }
        }

    private suspend fun consumeLiveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<Unit>>> =
        wrapApiRequest { notificationApi.consumeLiveEvents(clientId.value) }.map { webSocketEventFlow ->
            flow {
                webSocketEventFlow.collect(handleEvents(this))
            }
        }

    private suspend fun handleEvents(
        flowCollector: FlowCollector<WebSocketEvent<Unit>>,
    ): suspend (value: WebSocketEvent<ConsumableNotificationResponse>) -> Unit =
        { webSocketEvent ->
            when (webSocketEvent) {
                is WebSocketEvent.Open -> {
                    clearOnFirstWSMessage.emit(true)
                    flowCollector.emit(WebSocketEvent.Open(shouldProcessPendingEvents = false))
                }

                is WebSocketEvent.NonBinaryPayloadReceived -> {
                    flowCollector.emit(WebSocketEvent.NonBinaryPayloadReceived(webSocketEvent.payload))
                }

                is WebSocketEvent.Close -> {
                    flowCollector.emit(WebSocketEvent.Close(webSocketEvent.cause))
                }

                is WebSocketEvent.BinaryPayloadReceived -> {
                    when (val event: ConsumableNotificationResponse = webSocketEvent.payload) {
                        is ConsumableNotificationResponse.EventNotification -> {
                            if (clearOnFirstWSMessage.value) {
                                clearOnFirstWSMessage.emit(false)
                                clearProcessedEvents(event.data.event.id)
                            }
                            event.data.event.let { eventResponse ->
                                wrapStorageRequest {
                                    eventDAO.insertEvents(
                                        listOf(
                                            NewEventEntity(
                                                eventId = eventResponse.id,
                                                payload = KtxSerializer.json.encodeToString(eventResponse)
                                            )
                                        )
                                    )
                                }.onSuccess {
                                    event.data.deliveryTag?.let {
                                        ackEvent(it)
                                    }
                                    flowCollector.emit(WebSocketEvent.BinaryPayloadReceived(Unit))
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
                                            )
                                        )
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

    private suspend fun ackEvent(deliveryTag: ULong): Either<CoreFailure, Unit> {
        return currentClientId().fold(
            { it.left() },
            { clientId ->
                    notificationApi.acknowledgeEvents(
                        clientId.value,
                        EventAcknowledgeRequest(
                            type = AcknowledgeType.ACK,
                            data = AcknowledgeData(
                                deliveryTag = deliveryTag,
                                multiple = false // TODO when use multiple?
                            )
                        )
                    )
                // todo(ym) check for errors.
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

    private suspend fun liveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<Unit>>> =
        wrapApiRequest { notificationApi.listenToLiveEvents(clientId.value) }
            .map { webSocketEventFlow ->
                flow {
                    webSocketEventFlow
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
                        .collect(handleEvents(this))
                }
            }

    private suspend fun pendingEventsFlow(
        clientId: ClientId
    ) = flow<Either<CoreFailure, EventEnvelope>> {

        var hasMore = true
        var lastFetchedNotificationId = metadataDAO.valueByKey(LAST_PROCESSED_EVENT_ID_KEY)

        while (coroutineContext.isActive && hasMore) {
            val notificationsPageResult = getNextPendingEventsPage(lastFetchedNotificationId, clientId)

            if (notificationsPageResult.isSuccessful()) {
                hasMore = notificationsPageResult.value.hasMore
                lastFetchedNotificationId = notificationsPageResult.value.notifications.lastOrNull()?.id

                val entities = notificationsPageResult.value.notifications.mapNotNull { event ->
                    event.payload?.let {
                        NewEventEntity(
                            eventId = event.id,
                            payload = KtxSerializer.json.encodeToString(event)
                        )
                    }
                }

                eventDAO.insertEvents(entities)
            } else {
                hasMore = false
                emit(Either.Left(NetworkFailure.ServerMiscommunication(notificationsPageResult.kException)))
            }
        }
    }

    override fun parseExternalEvents(data: String): List<EventEnvelope> {
        val notificationResponse = Json.decodeFromString<NotificationResponse>(data)
        return notificationResponse.notifications.flatMap {
            eventMapper.fromDTO(it, isLive = false)
        }
    }

    override suspend fun lastProcessedEventId(): Either<StorageFailure, String> = wrapStorageRequest {
        metadataDAO.valueByKey(LAST_PROCESSED_EVENT_ID_KEY)
    }

    override suspend fun clearLastProcessedEventId(): Either<StorageFailure, Unit> = wrapStorageRequest {
        metadataDAO.deleteValue(LAST_PROCESSED_EVENT_ID_KEY)
    }

    override suspend fun fetchMostRecentEventId(): Either<CoreFailure, String> =
        currentClientId()
            .flatMap { currentClientId ->
                wrapApiRequest { notificationApi.mostRecentNotification(currentClientId.value) }
                    .map { it.id }
            }

    override suspend fun updateLastProcessedEventId(eventId: String): Either<StorageFailure, Unit> {
        return wrapStorageRequest { metadataDAO.insertValue(eventId, LAST_PROCESSED_EVENT_ID_KEY) }.flatMap {
            wrapStorageRequest {
                eventDAO.markEventAsProcessed(eventId)
            }
        }
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

    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 100
        const val LAST_PROCESSED_EVENT_ID_KEY = "last_processed_event_id"
    }
}
