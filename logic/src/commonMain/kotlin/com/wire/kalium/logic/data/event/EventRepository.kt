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
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.IncrementalSyncPhase
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeData
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
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
import kotlinx.io.IOException
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
import kotlin.uuid.Uuid

@Mockable
internal interface EventRepository {

    /**
     * Performs an acknowledgment of the missed event after performing a slow sync.
     */
    suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit>
    suspend fun liveEvents(): Either<CoreFailure, Flow<IncrementalSyncPhase>>
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
    suspend fun observeEvents(): Flow<List<EventEnvelope>>
    suspend fun setEventsAsProcessed(eventIds: List<String>): Either<StorageFailure, Unit>
}

@Suppress("TooManyFunctions", "LongParameterList")
internal class EventDataSource(
    private val notificationApi: NotificationApi,
    private val metadataDAO: MetadataDAO,
    private val eventDAO: EventDAO,
    private val currentClientId: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val clientRegistrationStorage: ClientRegistrationStorage,
    private val restartSlowSyncProcessForRecovery: RestartSlowSyncProcessForRecoveryUseCase,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId),
    logger: KaliumLogger
) : EventRepository {

    val logger = logger.withTextTag(TAG)

    private val clearOnFirstWSMessage = MutableStateFlow(false)
    private val sentinelMarker = AtomicReference<SentinelMarker>(SentinelMarker.None)

    override suspend fun observeEvents(): Flow<List<EventEnvelope>> {
        var lastEmittedEventId: String? = null
        return eventDAO.observeUnprocessedEvents().transform { eventEntities ->
            logger.d("got ${eventEntities.size} unprocessed events")
            if (eventEntities.isNotEmpty()) {
                logger.d("first unprocessed event ${eventEntities.firstOrNull()?.eventId}")
                logger.d("last unprocessed event ${eventEntities.lastOrNull()?.eventId}")
            }
            logger.d("current last emitted event id: $lastEmittedEventId")

            val emittedEventIndex = eventEntities.indexOfFirst { entity -> entity.eventId == lastEmittedEventId }

            if (emittedEventIndex == -1) {
                emit(eventEntities)
                return@transform
            }
            if (emittedEventIndex != eventEntities.lastIndex) {
                logger.d("filtered out ${emittedEventIndex + 1} events already marked as processed")
                emit(eventEntities.subList(emittedEventIndex + 1, eventEntities.size))
            } else {
                logger.d("no unprocessed events found")
            }
        }
            .onEach { entities ->
                entities.lastOrNull()?.let {
                    lastEmittedEventId = it.eventId
                }
            }
            .map { eventEntities ->
                eventEntities.map { entity ->
                    val payload: List<EventContentDTO>? =
                        entity.payload?.let {
                            KtxSerializer.json.decodeFromString<List<EventContentDTO>>(it)
                        }
                    eventMapper.fromStoredDTO(eventId = entity.eventId, payload = payload, isLive = entity.isLive)
                }
                    .flatten()
            }
    }

    override suspend fun acknowledgeMissedEvent(): Either<CoreFailure, Unit> {
        logger.d("Handling acknowledgeMissedEvent")
        return currentClientId().fold(
            { it.left() },
            {
                notificationApi.acknowledgeEvents(it.value, sentinelMarker.get().getMarker(), EventMapper.FULL_ACKNOWLEDGE_REQUEST)
                Unit.right()
            }
        )
    }

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)
    private suspend fun fetchEvents(): Either<CoreFailure, Unit> =
        currentClientId().fold({ Either.Left(it) }, { clientId -> fetchPendingEvents(clientId) })

    override suspend fun liveEvents(): Either<CoreFailure, Flow<IncrementalSyncPhase>> =
        currentClientId().flatMap { clientId ->
            val hasConsumableNotifications = clientRegistrationStorage.observeHasConsumableNotifications().firstOrNull()
            if (hasConsumableNotifications == true) {
                consumeLiveEventsFlow(clientId)
            } else {
                liveEventsFlow(clientId)
            }
        }

    private suspend fun consumeLiveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<IncrementalSyncPhase>> {
        sentinelMarker.set(SentinelMarker.Marker(Uuid.random().toString()))
        logger.d("Creating new sentinel marker [${sentinelMarker.get().getMarker()}] for this session.")
        return wrapApiRequest {
            notificationApi.consumeLiveEvents(clientId = clientId.value, markerId = sentinelMarker.get().getMarker())
        }.map { webSocketEventFlow ->
            flow {
                webSocketEventFlow.collect(handleEvents(this))
            }
        }
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun handleEvents(
        flowCollector: FlowCollector<IncrementalSyncPhase>
    ): suspend (value: WebSocketEvent<ConsumableNotificationResponse>) -> Unit =
        { webSocketEvent ->
            when (webSocketEvent) {
                is WebSocketEvent.Open -> {
                    clearOnFirstWSMessage.emit(true)
                    logger.d("set all unprocessed events as pending")
                    setAllUnprocessedEventsAsPending()
                    val isLegacyNotificationsSystem = webSocketEvent.shouldProcessPendingEvents
                    if (isLegacyNotificationsSystem) {
                        logger.d("fetch pending events from server")
                        val result = fetchEvents()
                        result.onFailure(::throwPendingEventException)
                    }
                    flowCollector.emit(
                        when (isLegacyNotificationsSystem) {
                            true -> IncrementalSyncPhase.ReadyToProcess // for legacy we already fetched the pending events.
                            false -> IncrementalSyncPhase.CatchingUp // for async we need to wait for the sentinel from the ws event stream.
                        }
                    )
                }

                is WebSocketEvent.NonBinaryPayloadReceived -> {
                    throw KaliumSyncException("NonBinaryPayloadReceived", CoreFailure.Unknown(null))
                }

                is WebSocketEvent.Close -> {
                    handleWebSocketClosure(webSocketEvent)
                }

                is WebSocketEvent.BinaryPayloadReceived -> {
                    val isLive = isWebsocketEventReceivedLive()
                    when (val event: ConsumableNotificationResponse = webSocketEvent.payload) {
                        is ConsumableNotificationResponse.EventNotification -> {
                            event.data.deliveryTag?.let {
                                // only log for async events
                                logger.d("Handling ConsumableNotificationResponse.EventNotification")
                            }
                            if (clearOnFirstWSMessage.value) {
                                clearOnFirstWSMessage.emit(false)
                                logger.d("clear processed events before ${event.data.event.id}")
                                clearProcessedEvents(event.data.event.id)
                            }
                            event.data.event.let { eventResponse ->
                                logger.d("insert event ${eventResponse.id} from WS")
                                wrapStorageRequest {
                                    eventDAO.insertEvents(
                                        listOf(
                                            NewEventEntity(
                                                eventId = eventResponse.id,
                                                payload = eventResponse.payload,
                                                transient = eventResponse.transient,
                                                isLive = isLive
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
                                    flowCollector.emit(
                                        when (isLive) {
                                            true -> IncrementalSyncPhase.ReadyToProcess
                                            false -> IncrementalSyncPhase.CatchingUp
                                        }
                                    )
                                }
                            }
                        }

                        ConsumableNotificationResponse.MissedNotification -> {
                            logger.d("Handling ConsumableNotificationResponse.MissedNotification")
                            acknowledgeMissedEvent()
                            restartSlowSyncProcessForRecovery()
                        }

                        is ConsumableNotificationResponse.SynchronizationNotification -> {
                            logger.d("Handling ConsumableNotificationResponse.SynchronizationNotification")
                            event.data.deliveryTag?.let { ackEvent(it) }
                            val currentMarker = sentinelMarker.get().getMarker()
                            if (event.data.markerId == currentMarker) {
                                logger.d("Handling current sentinel marker [${event.data.markerId}] for this session.")
                                sentinelMarker.set(SentinelMarker.None)
                                flowCollector.emit(IncrementalSyncPhase.ReadyToProcess)
                            } else {
                                logger.d("Skipping this sentinel marker [${event.data.markerId}] is not valid for this session.")
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
        logger.d("Handling ackEvent")
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

    @Suppress("ThrowsCount")
    private suspend fun handleWebSocketClosure(webSocketEvent: WebSocketEvent.Close<ConsumableNotificationResponse>) {
        when (val cause = webSocketEvent.cause) {
            null -> {
                logger.i("Websocket closed normally, will retry to keep connection alive")
                throw KaliumSyncException("Websocket closed normally", CoreFailure.Unknown(null))
            }

            is IOException ->
                throw KaliumSyncException("Websocket disconnected", NetworkFailure.NoNetworkConnection(cause))

            else ->
                throw KaliumSyncException("Unknown Websocket error: $cause, message: ${cause.message}", CoreFailure.Unknown(cause))
        }
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

    private suspend fun liveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<IncrementalSyncPhase>> =
        wrapApiRequest { notificationApi.listenToLiveEvents(clientId.value) }
            .map { webSocketEventFlow ->
                flow {
                    webSocketEventFlow
                        .buffer(capacity = Channel.UNLIMITED, onBufferOverflow = BufferOverflow.SUSPEND)
                        .map {
                            when (it) {
                                is WebSocketEvent.BinaryPayloadReceived<EventResponseToStore> ->
                                    WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>(
                                        ConsumableNotificationResponse.EventNotification(
                                            EventDataDTO(
                                                null,
                                                it.payload
                                            )
                                        )
                                    )

                                is WebSocketEvent.Close<EventResponseToStore> -> WebSocketEvent.Close(it.cause)
                                is WebSocketEvent.NonBinaryPayloadReceived<EventResponseToStore> ->
                                    WebSocketEvent.NonBinaryPayloadReceived(it.payload)

                                is WebSocketEvent.Open<EventResponseToStore> -> WebSocketEvent.Open(it.shouldProcessPendingEvents)
                            }
                        }
                        .collect(handleEvents(this))
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
                            payload = event.payload,
                            isLive = false,
                            transient = event.transient
                        )
                    }
                }
                logger.d("inserting ${entities.size} events from pending notifications, hasMore: $hasMore")
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
        logger.i("Pending events collection finished. Collecting Live events.")
        return Either.Right(Unit)
    }

    override fun parseExternalEvents(data: String): List<EventEnvelope> {
        val notificationResponse = Json.decodeFromString<NotificationResponse>(data)
        return notificationResponse.notifications.flatMap {
            eventMapper.fromDTO(it.toEventResponse(), isLive = false)
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

    override suspend fun setEventsAsProcessed(eventIds: List<String>): Either<StorageFailure, Unit> = wrapStorageRequest {
        eventDAO.markEventsAsProcessed(eventIds)
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

    private fun throwPendingEventException(failure: CoreFailure) {
        val networkCause = (failure as? NetworkFailure.ServerMiscommunication)?.rootCause
        val isEventNotFound = networkCause is KaliumException.InvalidRequestError
                && networkCause.errorResponse.code == HttpStatusCode.NotFound.value
        throw KaliumSyncException(
            message = "Failure to fetch pending events, aborting Incremental Sync",
            coreFailureCause = if (isEventNotFound) CoreFailure.SyncEventOrClientNotFound else failure
        )
    }

    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 100

        // DO NOT CHANGE THE NAME IT IS INTENTIONAL THAT LAST_SAVED_EVENT_ID_KEY VALUE IS last_processed_event_id
        // CHANGE IT AND YOU WILL BE FIRED
        // ALSO KUBAZ WILL THROW A CURSE AT YOUR FAMILY
        // SERIOUSLY, YOUR PHONE WILL AUTOCORRECT EVERYTHING TO "BANANA" FOR A MONTH
        // YOUR COFFEE WILL TASTE LIKE SADNESS AND BROKEN DREAMS
        // LEGACY REASONS - THIS NAME IS SACRED AND MUST NOT BE TOUCHED
        // CHANGING THIS WILL CAUSE THE BUILD SERVERS TO PERSONALLY HUNT YOU DOWN
        // THE LAST PERSON WHO CHANGED THIS IS NOW WORKING AS A MIME IN FRANCE
        // DON'T BE THAT PERSON. JUST DON'T.
        const val LAST_SAVED_EVENT_ID_KEY = "last_processed_event_id"
        const val TAG = "[EventRepository]"
    }
}
