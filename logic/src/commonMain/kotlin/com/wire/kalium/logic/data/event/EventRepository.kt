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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.MetadataDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import kotlin.coroutines.coroutineContext

interface EventRepository {

    suspend fun pendingEvents(): Flow<Either<CoreFailure, EventEnvelope>>
    suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<EventEnvelope>>>
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
}

class EventDataSource(
    private val notificationApi: NotificationApi,
    private val metadataDAO: MetadataDAO,
    private val currentClientId: CurrentClientIdProvider,
    private val selfUserId: UserId,
    private val eventMapper: EventMapper = MapperProvider.eventMapper(selfUserId)
) : EventRepository {

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)
    override suspend fun pendingEvents(): Flow<Either<CoreFailure, EventEnvelope>> =
        currentClientId().fold({ flowOf(Either.Left(it)) }, { clientId -> pendingEventsFlow(clientId) })

    override suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<EventEnvelope>>> =
        currentClientId().flatMap { clientId ->
            // todo(ym) check if it has consumable notifications is available for the client
            // todo(ym) also cache this capabilities value?
            val hasConsumableNotifications = false // check
            if (hasConsumableNotifications) {
                liveEventsFlow(clientId)
            } else {
                consumeLiveEventsFlow(clientId)
            }
        }

    private suspend fun consumeLiveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<EventEnvelope>>> =
        wrapApiRequest { notificationApi.consumeLiveEvents(clientId.value) }.map { webSocketEventFlow ->
            flow {
                webSocketEventFlow.collect { webSocketEvent ->
                    when (webSocketEvent) {
                        is WebSocketEvent.Open -> {
                            emit(WebSocketEvent.Open())
                        }

                        is WebSocketEvent.NonBinaryPayloadReceived -> {
                            emit(WebSocketEvent.NonBinaryPayloadReceived(webSocketEvent.payload))
                        }

                        is WebSocketEvent.Close -> {
                            emit(WebSocketEvent.Close(webSocketEvent.cause))
                        }

                        is WebSocketEvent.BinaryPayloadReceived -> {
                            val events = eventMapper.fromDTO(webSocketEvent.payload)
                            events.forEach { eventEnvelope ->
                                emit(WebSocketEvent.BinaryPayloadReceived(eventEnvelope))
                            }
                        }
                    }
                }
            }
        }

    private suspend fun liveEventsFlow(clientId: ClientId): Either<NetworkFailure, Flow<WebSocketEvent<EventEnvelope>>> =
        wrapApiRequest { notificationApi.listenToLiveEvents(clientId.value) }.map { webSocketEventFlow ->
            flow {
                webSocketEventFlow.collect { webSocketEvent ->
                    when (webSocketEvent) {
                        is WebSocketEvent.Open -> {
                            emit(WebSocketEvent.Open())
                        }

                        is WebSocketEvent.NonBinaryPayloadReceived -> {
                            emit(WebSocketEvent.NonBinaryPayloadReceived(webSocketEvent.payload))
                        }

                        is WebSocketEvent.Close -> {
                            emit(WebSocketEvent.Close(webSocketEvent.cause))
                        }

                        is WebSocketEvent.BinaryPayloadReceived -> {
                            val events = eventMapper.fromDTO(webSocketEvent.payload, true)
                            events.forEach { eventEnvelope ->
                                emit(WebSocketEvent.BinaryPayloadReceived(eventEnvelope))
                            }
                        }
                    }
                }
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

                notificationsPageResult.value.notifications.flatMap {
                    eventMapper.fromDTO(it, isLive = false)
                }.forEach { event ->
                    if (!coroutineContext.isActive) {
                        return@flow
                    }
                    emit(Either.Right(event))
                }
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

    override suspend fun updateLastProcessedEventId(eventId: String) =
        wrapStorageRequest { metadataDAO.insertValue(eventId, LAST_PROCESSED_EVENT_ID_KEY) }

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
