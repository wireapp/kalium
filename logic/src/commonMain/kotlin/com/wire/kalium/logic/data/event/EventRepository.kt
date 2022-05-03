package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationResponse
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.event.EventInfoStorage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flattenConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

interface EventRepository {
    suspend fun events(): Flow<Either<CoreFailure, Event>>
    suspend fun pendingEvents(): Flow<Either<CoreFailure, Event>>
    suspend fun updateLastProcessedEventId(eventId: String)
}

class EventDataSource(
    private val notificationApi: NotificationApi,
    private val eventInfoStorage: EventInfoStorage,
    private val clientRepository: ClientRepository,
    private val eventMapper: EventMapper = MapperProvider.eventMapper()
) : EventRepository {

    // TODO: handle Missing notification response (notify user that some messages are missing)

    override suspend fun events(): Flow<Either<CoreFailure, Event>> = suspending {
        clientRepository.currentClientId().coFold({
            flowOf(Either.Left(CoreFailure.MissingClientRegistration))
        }, { clientId ->
            val pendingEventsFlow = pendingEventsFlow(clientId)
            val liveEventsFlow = liveEventsFlow(clientId)
            flowOf(pendingEventsFlow, liveEventsFlow).flattenConcat()
        })
    }

    override suspend fun pendingEvents(): Flow<Either<CoreFailure, Event>> = suspending {
        clientRepository.currentClientId().coFold(
            { flowOf(Either.Left(it)) },
            { clientId -> pendingEventsFlow(clientId) }
        )
    }

    private suspend fun liveEventsFlow(clientId: ClientId): Flow<Either<CoreFailure, Event>> =
        notificationApi.listenToLiveEvents(clientId.value)
            .map {
                println("Mapping eventResponse from LiveFlow ${it}")
                eventMapper.fromDTO(it).asFlow()
            }
            .flattenConcat()
            .filterNotNull()
            .map { Either.Right(it) }
            .catch { e -> Either.Left(CoreFailure.Unknown(e)) }

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

    override suspend fun updateLastProcessedEventId(eventId: String) {
        eventInfoStorage.lastProcessedId = eventId
    }

    private suspend fun getNextPendingEventsPage(
        lastFetchedNotificationId: String?,
        clientId: ClientId
    ): NetworkResponse<NotificationResponse> =
        lastFetchedNotificationId?.let {
            notificationApi.notificationsByBatch(NOTIFICATIONS_QUERY_SIZE, clientId.value, it)
        } ?: notificationApi.getAllNotifications(NOTIFICATIONS_QUERY_SIZE, clientId.value)


    private companion object {
        const val NOTIFICATIONS_QUERY_SIZE = 500
    }
}
