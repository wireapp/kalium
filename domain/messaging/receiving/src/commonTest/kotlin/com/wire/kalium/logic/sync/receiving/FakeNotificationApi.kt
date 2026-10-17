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

import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Legacy notification stream: [websocket] events, then REST pages by exclusive cursor. */
internal class FakeNotificationApi(
    private val websocket: () -> Flow<WebSocketEvent<EventResponseToStore>>,
    private val pages: suspend (cursor: String?) -> NotificationResponse,
) : NotificationApi {
    val requests = mutableListOf<String>()

    constructor(websocket: List<WebSocketEvent<EventResponseToStore>>, pages: Map<String?, NotificationResponse>) : this(
        { kotlinx.coroutines.flow.flowOf(*websocket.toTypedArray()) },
        pages.toMutableMap().let { remaining -> { cursor -> checkNotNull(remaining.remove(cursor)) { "Unexpected cursor $cursor" } } },
    )

    val requestedCursors get() = requests.filter { it.startsWith("page:") }.map { it.removePrefix("page:").ifEmpty { null } }

    override suspend fun mostRecentNotification(queryClient: String): NetworkResponse<EventResponse> = error("Unused")

    override suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String) = page(querySince)

    override suspend fun oldestNotification(queryClient: String): NetworkResponse<EventResponseToStore> = error("Unused")

    override suspend fun getAllNotifications(querySize: Int, queryClient: String) = page(null)

    override suspend fun listenToLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<EventResponseToStore>>> {
        requests += "connect"
        return NetworkResponse.Success(websocket(), emptyMap(), 200)
    }

    override suspend fun consumeLiveEvents(
        clientId: String,
        markerId: String,
    ): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> = error("Unused")

    override suspend fun acknowledgeEvents(clientId: String, markerId: String, eventAcknowledgeRequest: EventAcknowledgeRequest) =
        error("Unused")

    private suspend fun page(cursor: String?): NetworkResponse<NotificationResponse> {
        requests += "page:${cursor.orEmpty()}"
        return try {
            NetworkResponse.Success(pages(cursor), emptyMap(), 200)
        } catch (failure: KaliumException) {
            NetworkResponse.Error(failure)
        }
    }

    companion object {
        const val PAYLOAD = "[]"

        fun event(id: String) = EventResponseToStore(id, PAYLOAD)

        fun page(vararg ids: String) = NotificationResponse("2026-01-01T00:00:00Z", false, ids.map(::event))
    }
}

internal suspend fun UserDatabaseBuilder.eventIds() = eventDAO.observeEvents(0).first().map { it.eventId }

internal suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) {
    val database = TestUserDatabase(UserIDEntity("event-ingestion", "example.com"), StandardTestDispatcher(testScheduler))
    try {
        block(database.builder)
    } finally {
        database.builder.close()
        database.delete()
    }
}
