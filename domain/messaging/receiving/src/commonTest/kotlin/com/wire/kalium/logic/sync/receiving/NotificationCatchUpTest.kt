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
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.awaitCancellation
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

    private fun catchUp(api: FakeNotificationApi, database: UserDatabaseBuilder) = NotificationCatchUp(
        EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID),
        closeWebSockets = { api.requests += "close" },
    )

    private companion object {
        const val CLIENT_ID = "client"
    }
}
