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
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LiveHoldTest {

    @Test
    fun socketStaysOpenAndStoresEventsWhileTheWorkRuns() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({
                flow {
                    emit(WebSocketEvent.Open())
                    emit(WebSocketEvent.BinaryPayloadReceived(event("Y")))
                    awaitCancellation()
                }
            }) { cursor -> if (cursor == null) page("A") else page() }

            val held = hold(api, database).run(CONNECT, consumable = false) { started ->
                delay(1.seconds)
                assertFalse("close" in api.requests)
                Triple(started, database.eventIds(), api.requests.toList())
            }

            val (started, storedDuringWork, requestsDuringWork) = held.value
            assertEquals(CatchUpOutcome.LIVE, started)
            assertEquals(listOf("A", "Y"), storedDuringWork)
            assertEquals(listOf("connect", "page:"), requestsDuringWork)
            // Like Android's one-shot sync, nothing is paged after closing.
            assertEquals(listOf("connect", "page:", "close"), api.requests)
            assertEquals(CatchUpOutcome.LIVE, held.closed)
        }
    }

    @Test
    fun brokenSocketFailsTheHoldWithoutRestPaging() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flowOf(WebSocketEvent.Close(IOException("offline"))) }) { cursor ->
                error("Unexpected page $cursor")
            }

            val held = hold(api, database).run(CONNECT, consumable = false) { started -> started }

            assertEquals(CatchUpOutcome.FAILED, held.value)
            assertEquals(listOf("connect", "close"), api.requests)
            assertTrue(database.eventIds().isEmpty())
            assertEquals(CatchUpOutcome.FAILED, held.closed)
        }
    }

    @Test
    fun slowSocketTimesOutTheHoldWithoutRestPaging() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flow { awaitCancellation() } }) { cursor ->
                error("Unexpected page $cursor")
            }

            val held = hold(api, database).run(CONNECT, consumable = false) { started -> started }

            assertEquals(CatchUpOutcome.BUDGET, held.value)
            assertEquals(listOf("connect", "close"), api.requests)
            assertEquals(CatchUpOutcome.BUDGET, held.closed)
        }
    }

    @Test
    fun failingWorkStillClosesTheSocket() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi({ flow { emit(WebSocketEvent.Open()); awaitCancellation() } }) { page() }

            assertFailsWith<IllegalStateException> {
                hold(api, database).run(CONNECT, consumable = false) { error("Send failed") }
            }

            assertEquals(listOf("connect", "page:", "close"), api.requests)
        }
    }

    @Test
    fun consumableHoldAcknowledgesEventsWhileTheWorkRuns() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = { marker ->
                flow {
                    emit(WebSocketEvent.Open(shouldProcessPendingEvents = false))
                    emit(WebSocketEvent.BinaryPayloadReceived(synchronization(2UL, marker)))
                    emit(consumableEvent("B", 3UL))
                    awaitCancellation()
                }
            }

            val held = hold(api, database).run(CONNECT, consumable = true) { started ->
                delay(1.seconds)
                started to database.eventIds()
            }

            assertEquals(CatchUpOutcome.LIVE to listOf("B"), held.value)
            assertEquals(listOf("consume", "ack:2", "ack:3", "close"), api.requests)
            assertEquals(CatchUpOutcome.LIVE, held.closed)
        }
    }

    @Test
    fun consumableFailureAfterLiveIsReportedWhenTheHoldCloses() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = { marker ->
                flow {
                    emit(WebSocketEvent.Open(shouldProcessPendingEvents = false))
                    emit(WebSocketEvent.BinaryPayloadReceived(synchronization(2UL, marker)))
                    emit(consumableEvent("B", 3UL))
                    awaitCancellation()
                }
            }
            api.acknowledge = { request ->
                if (request.data?.deliveryTag == 3UL) NetworkResponse.Error(KaliumException.NoNetwork())
                else NetworkResponse.Success(Unit, emptyMap(), 200)
            }

            val held = hold(api, database).run(CONNECT, consumable = true) { started ->
                delay(1.seconds)
                started
            }

            assertEquals(CatchUpOutcome.LIVE, held.value)
            assertEquals(CatchUpOutcome.FAILED, held.closed)
        }
    }

    @Test
    fun missedNotificationsLeaveTheWorkToTheApp() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(emptyList(), emptyMap())
            api.consumable = {
                flowOf(
                    WebSocketEvent.Open(shouldProcessPendingEvents = false),
                    WebSocketEvent.BinaryPayloadReceived(ConsumableNotificationResponse.MissedNotification),
                )
            }

            val held = hold(api, database).run(CONNECT, consumable = true) { it }

            assertEquals(CatchUpOutcome.NEEDS_SLOW_SYNC, held.value)
            assertEquals(listOf("consume", "close"), api.requests)
        }
    }

    private fun hold(api: FakeNotificationApi, database: UserDatabaseBuilder) = LiveHold(
        EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID),
        closeWebSockets = { api.requests += "close" },
    )

    private fun synchronization(deliveryTag: ULong, marker: String) =
        ConsumableNotificationResponse.SynchronizationNotification(SynchronizationDataDTO(deliveryTag, marker))

    private fun consumableEvent(id: String, deliveryTag: ULong) = WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>(
        ConsumableNotificationResponse.EventNotification(EventDataDTO(deliveryTag, event(id)))
    )

    private companion object {
        const val CLIENT_ID = "client"
        val CONNECT = 4.seconds
    }
}
