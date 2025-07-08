/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.event.stream.legacy

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.stream.BaseEventStream
import com.wire.kalium.logic.data.event.stream.EventStreamStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.event.EventDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.coroutines.coroutineContext

/**
 * Backed by the legacy REST + Websocket API.
 */
internal class LegacyEventStream(
    private val networkRequestProvider: () -> Flow<WebSocketEvent<EventResponse>>,
    private val eventDAO: EventDAO,
    private val selfUserId: UserId,
    private val logger: KaliumLogger = kaliumLogger
) : BaseEventStream(eventDAO, selfUserId) {
    override suspend fun eventStreamStatusFlow(): Flow<EventStreamStatus> = flow<EventStreamStatus> {
        this.ca
        networkRequestProvider().collect { websocketEvent ->
            when (websocketEvent) {
                is WebSocketEvent.Open<*> -> {
                    emit(EventStreamStatus.CatchingUp)
                    fetchPendingEvents(clientId).onFailure { coreFailure ->
                        throw KaliumSyncException("Failure fetching pending events", coreFailure)
                    }.onSuccess { event ->
                        emit(EventStreamStatus.Live(event?.id))
                    }
                }

                is WebSocketEvent.BinaryPayloadReceived<EventResponse> -> {
                    // TODO: Handle legacy events from websocket
                    insertNewEventsIntoStorage(listOf(websocketEvent.payload))
                }

                is WebSocketEvent.Close<*> -> onClose()
                is WebSocketEvent.NonBinaryPayloadReceived<*> -> {
                    // TODO: LOG
                }

            }
        }
    }

    private suspend fun fetchPendingEvents(clientId: ClientId): Either<CoreFailure, EventResponse?> {
        var hasMore = true
        var lastPreviouslyKnownNotificationId = metadataDAO.valueByKey(LAST_SAVED_EVENT_ID_KEY)
        var newestFetchedEvent: EventResponse? = null

        while (coroutineContext.isActive && hasMore) {
            val notificationsPageResult = getNextPendingEventsPage(lastPreviouslyKnownNotificationId, clientId)

            if (notificationsPageResult.isSuccessful()) {
                hasMore = notificationsPageResult.value.hasMore
                lastPreviouslyKnownNotificationId = notificationsPageResult.value.notifications.lastOrNull()?.id
                newestFetchedEvent = notificationsPageResult.value.notifications.lastOrNull()
                insertNewEventsIntoStorage(notificationsPageResult.value.notifications)
            } else {
                return Either.Left(NetworkFailure.ServerMiscommunication(notificationsPageResult.kException))
            }
        }
        kaliumLogger.i("Pending events collection finished. Collecting Live events.")
        return Either.Right(newestFetchedEvent)
    }

    private suspend fun getNextPendingEventsPage(
        lastFetchedNotificationId: String?,
        clientId: ClientId
    ): NetworkResponse<NotificationResponse> = lastFetchedNotificationId?.let {
        notificationApi.notificationsByBatch(NOTIFICATIONS_QUERY_SIZE, clientId.value, it)
    } ?: notificationApi.getAllNotifications(NOTIFICATIONS_QUERY_SIZE, clientId.value)
}
