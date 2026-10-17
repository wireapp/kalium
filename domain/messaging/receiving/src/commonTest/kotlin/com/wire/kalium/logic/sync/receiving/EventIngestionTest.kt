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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventIngestionTest {

    @Test
    fun legacyCollectionStoppedAtReadyLeavesBufferedFramesForTheNextPendingFetch() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(event("Y"))),
                mapOf(null to page("A", "B"), "B" to page("Y")),
            )
            val ingestion = EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID)

            assertIs<Either.Right<Flow<IngestionPhase>>>(ingestion.legacy()).value.first { it == IngestionPhase.READY }
            assertEquals(listOf("A", "B"), database.eventIds())

            assertEquals(Either.Right(Unit), ingestion.fetchPending())
            assertEquals(listOf("A", "B", "Y"), database.eventIds())
            assertEquals(listOf(null, "B"), api.requestedCursors)
        }
    }

    @Test
    fun withoutFirstEventCleanupProcessedHistoryIsKept() = runTest {
        withDatabase { database ->
            database.notificationEventStore.storePage(null, listOf(NewEventEntity("A", PAYLOAD, false, false)))
            database.eventDAO.markEventAsProcessed("A")
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(event("Y"))),
                mapOf("A" to page()),
            )

            assertIs<Either.Right<Flow<IngestionPhase>>>(
                EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID).legacy()
            ).value.collect()

            assertEquals(listOf("A", "Y"), database.eventIds())
        }
    }

    private suspend fun UserDatabaseBuilder.eventIds() = eventDAO.observeEvents(0).first().map { it.eventId }

    private suspend fun TestScope.withDatabase(block: suspend (UserDatabaseBuilder) -> Unit) {
        val database = TestUserDatabase(UserIDEntity("event-ingestion", "example.com"), StandardTestDispatcher(testScheduler))
        try {
            block(database.builder)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    private class FakeNotificationApi(
        private val websocket: List<WebSocketEvent<EventResponseToStore>>,
        pages: Map<String?, NotificationResponse>,
    ) : NotificationApi {
        private val pages = pages.toMutableMap()
        val requestedCursors = mutableListOf<String?>()

        override suspend fun mostRecentNotification(queryClient: String): NetworkResponse<EventResponse> = error("Unused")

        override suspend fun notificationsByBatch(querySize: Int, queryClient: String, querySince: String) = page(querySince)

        override suspend fun oldestNotification(queryClient: String): NetworkResponse<EventResponseToStore> = error("Unused")

        override suspend fun getAllNotifications(querySize: Int, queryClient: String) = page(null)

        override suspend fun listenToLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<EventResponseToStore>>> =
            NetworkResponse.Success(websocket.asFlow(), emptyMap(), 200)

        override suspend fun consumeLiveEvents(
            clientId: String,
            markerId: String,
        ): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> = error("Unused")

        override suspend fun acknowledgeEvents(clientId: String, markerId: String, eventAcknowledgeRequest: EventAcknowledgeRequest) =
            error("Unused")

        private fun page(cursor: String?): NetworkResponse<NotificationResponse> {
            requestedCursors += cursor
            return NetworkResponse.Success(checkNotNull(pages.remove(cursor)) { "Unexpected cursor $cursor" }, emptyMap(), 200)
        }
    }

    private companion object {
        const val CLIENT_ID = "client"
        const val PAYLOAD = "[]"

        fun event(id: String) = EventResponseToStore(id, PAYLOAD)

        fun page(vararg ids: String) = NotificationResponse("2026-01-01T00:00:00Z", false, ids.map(::event))
    }
}
