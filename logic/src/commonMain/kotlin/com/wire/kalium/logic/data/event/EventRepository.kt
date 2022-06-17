package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationResponse
import com.wire.kalium.network.api.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.event.EventInfoStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

interface EventRepository {
    suspend fun pendingEvents(): Flow<Either<CoreFailure, Event>>
    suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<Event>>>
    suspend fun updateLastProcessedEventId(eventId: String)

    /**
     * Gets the last processed event ID, if it exists.
     * Otherwise, it attempts to fetch the last event stored
     * in remote.
     */
    suspend fun lastEventId(): Either<CoreFailure, String>
}

class EventDataSource(
    private val notificationApi: NotificationApi,
    private val eventInfoStorage: EventInfoStorage,
    private val clientRepository: ClientRepository,
    private val eventMapper: EventMapper = MapperProvider.eventMapper()
) : EventRepository {

    // TODO(edge-case): handle Missing notification response (notify user that some messages are missing)

    override suspend fun pendingEvents(): Flow<Either<CoreFailure, Event>> =
        clientRepository.currentClientId().fold({ flowOf(Either.Left(it)) }, { clientId -> pendingEventsFlow(clientId) })

    override suspend fun liveEvents(): Either<CoreFailure, Flow<WebSocketEvent<Event>>> =
        clientRepository.currentClientId().map { clientId -> liveEventsFlow(clientId) }

    private suspend fun liveEventsFlow(clientId: ClientId): Flow<WebSocketEvent<Event>> =
        notificationApi.listenToLiveEvents(clientId.value).map { webSocketEvent ->
            when (webSocketEvent) {
                is WebSocketEvent.Open -> {
                    flowOf(WebSocketEvent.Open())
                }

                is WebSocketEvent.NonBinaryPayloadReceived -> {
                    flowOf(WebSocketEvent.NonBinaryPayloadReceived<Event>(webSocketEvent.payload))
                }

                is WebSocketEvent.Close -> {
                    flowOf(WebSocketEvent.Close(webSocketEvent.cause))
                }

                is WebSocketEvent.BinaryPayloadReceived -> {
                    eventMapper.fromDTO(webSocketEvent.payload).asFlow().map { WebSocketEvent.BinaryPayloadReceived(it) }
                }
            }
        }.flattenConcat()

    private suspend fun pendingEventsFlow(
        clientId: ClientId
    ) = flow<Either<CoreFailure, Event>> {

        var hasMore = true
        var lastFetchedNotificationId = eventInfoStorage.lastProcessedId

        while (coroutineContext.isActive && hasMore) {
            val notificationsPageResult = getNextPendingEventsPage(lastFetchedNotificationId, clientId)

            if (notificationsPageResult.isSuccessful()) {
                hasMore = notificationsPageResult.value.hasMore
                lastFetchedNotificationId = notificationsPageResult.value.notifications.lastOrNull()?.id

                notificationsPageResult.value.notifications.flatMap(eventMapper::fromDTO).forEach { event ->
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

    override suspend fun lastEventId(): Either<CoreFailure, String> = eventInfoStorage.lastProcessedId?.let {
        Either.Right(it)
    } ?: run {
        clientRepository.currentClientId().flatMap { clientId ->
            wrapApiRequest { notificationApi.lastNotification(clientId.value) }.map { it.id }
        }
    }

    override suspend fun updateLastProcessedEventId(eventId: String) {
        eventInfoStorage.lastProcessedId = eventId
    }

    private suspend fun getNextPendingEventsPage(
        lastFetchedNotificationId: String?, clientId: ClientId
    ): NetworkResponse<NotificationResponse> = lastFetchedNotificationId?.let {
        notificationApi.notificationsByBatch(NOTIFICATIONS_QUERY_SIZE, clientId.value, it)
    } ?: notificationApi.getAllNotifications(NOTIFICATIONS_QUERY_SIZE, clientId.value)

    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 100
    }
}
