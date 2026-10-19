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

import com.wire.kalium.logic.sync.receiving.FakeNotificationApi.Companion.event
import com.wire.kalium.logic.sync.receiving.FakeNotificationApi.Companion.page
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.SynchronizationDataDTO
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import kotlinx.coroutines.awaitCancellation
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class NotificationCatchUpTest {

    @Test
    fun socketClosesAtLiveWithoutAnotherPage() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(event("Y"))),
                mapOf(null to page("A", "B")),
            )

            assertEquals(CatchUpOutcome.LIVE, catchUp(api, database).run(10.seconds))

            assertEquals(listOf("connect", "page:", "close"), api.requests)
            // A frame received while paging is dropped at close; the next sync's paging stores it.
            assertEquals(listOf("A", "B"), database.eventIds())
        }
    }

    @Test
    fun budgetKeepsStoredPagesAndClosesTheSocket() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flowOf(WebSocketEvent.Open()) }) { cursor ->
                if (cursor == null) page("A").copy(hasMore = true) else awaitCancellation()
            }

            assertEquals(CatchUpOutcome.BUDGET, catchUp(api, database).run(1.seconds))

            assertEquals(listOf("connect", "page:", "page:A", "close"), api.requests)
            assertEquals(listOf("A"), database.eventIds())
        }
    }

    @Test
    fun brokenSocketFailsWithoutRestPaging() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flowOf(WebSocketEvent.Close(IOException("offline"))) }) { cursor ->
                error("Unexpected page $cursor")
            }

            assertEquals(CatchUpOutcome.FAILED, catchUp(api, database).run(10.seconds))

            assertEquals(listOf("connect", "close"), api.requests)
            assertTrue(database.eventIds().isEmpty())
        }
    }

    @Test
    fun failedPagingFailsTheCatchUp() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flowOf(WebSocketEvent.Open()) }) {
                throw KaliumException.NoNetwork()
            }

            assertEquals(CatchUpOutcome.FAILED, catchUp(api, database).run(10.seconds))
            assertTrue(database.eventIds().isEmpty())
        }
    }

    @Test
    fun consumableCatchUpStoresEachEventBeforeAcknowledgingItUntilItsMarker() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            val storedAtAck = mutableListOf<Boolean>()
            api.consumable = { marker ->
                flowOf(
                    WebSocketEvent.Open(shouldProcessPendingEvents = false),
                    consumableEvent("A", 1UL),
                    // Another session's marker does not end this catch-up.
                    WebSocketEvent.BinaryPayloadReceived(
                        ConsumableNotificationResponse.SynchronizationNotification(SynchronizationDataDTO(9UL, "foreign"))
                    ),
                    WebSocketEvent.BinaryPayloadReceived(
                        ConsumableNotificationResponse.SynchronizationNotification(SynchronizationDataDTO(2UL, marker))
                    ),
                    consumableEvent("B", 3UL),
                )
            }
            api.acknowledge = {
                storedAtAck += database.eventDAO.getEventById("A") != null
                NetworkResponse.Success(Unit, emptyMap(), 200)
            }

            assertEquals(CatchUpOutcome.LIVE, catchUp(api, database).run(10.seconds, consumable = true))

            // Events after the marker stay unacknowledged and are delivered again.
            assertEquals(listOf("consume", "ack:1", "ack:9", "ack:2", "close"), api.requests)
            assertEquals(listOf(true, true, true), storedAtAck)
            assertEquals(listOf("A"), database.eventIds())
        }
    }

    @Test
    fun missedNotificationsStopWithoutAcknowledgingThem() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = {
                flowOf(
                    WebSocketEvent.Open(shouldProcessPendingEvents = false),
                    WebSocketEvent.BinaryPayloadReceived(ConsumableNotificationResponse.MissedNotification),
                )
            }

            assertEquals(CatchUpOutcome.NEEDS_SLOW_SYNC, catchUp(api, database).run(10.seconds, consumable = true))

            assertEquals(listOf("consume", "close"), api.requests)
        }
    }

    @Test
    fun failedAcknowledgementFailsConsumableCatchUpAfterStoringTheEvent() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = { flowOf(WebSocketEvent.Open(shouldProcessPendingEvents = false), consumableEvent("A", 1UL)) }
            api.acknowledge = { NetworkResponse.Error(KaliumException.NoNetwork()) }

            assertEquals(CatchUpOutcome.FAILED, catchUp(api, database).run(10.seconds, consumable = true))

            assertEquals(listOf("consume", "ack:1", "close"), api.requests)
            assertEquals(listOf("A"), database.eventIds())
        }
    }

    @Test
    fun eventThatCannotBeStoredIsNeverAcknowledged() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = { flowOf(WebSocketEvent.Open(shouldProcessPendingEvents = false), consumableEvent("A", 1UL)) }
            val failingStorage = object : EventDAO by database.eventDAO {
                override suspend fun insertEvents(events: List<NewEventEntity>) = error("Storage unavailable")
            }
            val catchUp = NotificationCatchUp(
                EventIngestion(api, failingStorage, database.metadataDAO, CLIENT_ID),
                closeWebSockets = { api.requests += "close" },
            )

            assertEquals(CatchUpOutcome.FAILED, catchUp.run(10.seconds, consumable = true))

            assertEquals(listOf("consume", "close"), api.requests)
        }
    }

    private fun catchUp(api: FakeNotificationApi, database: UserDatabaseBuilder) = NotificationCatchUp(
        EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID),
        closeWebSockets = { api.requests += "close" },
    )

    private fun consumableEvent(id: String, deliveryTag: ULong) = WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>(
        ConsumableNotificationResponse.EventNotification(EventDataDTO(deliveryTag, event(id)))
    )

    private companion object {
        const val CLIENT_ID = "client"
    }
}
