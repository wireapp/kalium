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
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.persistence.dao.event.NewEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventIngestionTest {

    @Test
    fun legacyCollectionStoppedAtReadyLeavesBufferedFramesForTheNextPendingFetch() = runTest {
        withDatabase { database ->
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(FakeNotificationApi.event("Y"))),
                mapOf(null to FakeNotificationApi.page("A", "B"), "B" to FakeNotificationApi.page("Y")),
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
            database.notificationEventStore.storePage(null, listOf(NewEventEntity("A", FakeNotificationApi.PAYLOAD, false, false)))
            database.eventDAO.markEventAsProcessed("A")
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(FakeNotificationApi.event("Y"))),
                mapOf("A" to FakeNotificationApi.page()),
            )

            assertIs<Either.Right<Flow<IngestionPhase>>>(
                EventIngestion(api, database.eventDAO, database.metadataDAO, CLIENT_ID).legacy()
            ).value.collect()

            assertEquals(listOf("A", "Y"), database.eventIds())
        }
    }

    private companion object {
        const val CLIENT_ID = "client"
    }
}
