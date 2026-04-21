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

package com.wire.kalium.logic.data.event

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.dao.event.EventEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryIntegrationTest {

    private val dispatcher = StandardTestDispatcher()

    private val databases = mutableListOf<TestUserDatabase>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        databases.forEach(TestUserDatabase::delete)
        databases.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun givenPendingPagesAndBufferedLiveEvents_whenCollectingLegacySync_thenShouldStorePendingPagesBeforeBufferedLiveEvents() =
        runTest {
            val harness = newHarness()
            val repository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Z")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))
                    ),
                    pendingPages = mapOf(
                        null to listOf(notificationPage(listOf("A", "B", "C"), hasMore = true)),
                        "C" to listOf(notificationPage(listOf("E", "F", "G"), hasMore = false))
                    )
                )
            )

            repository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())
            assertEquals(
                listOf(false, false, false, false, false, false, true, true),
                harness.unprocessedLiveFlags()
            )
            assertLastSavedEventId(repository, "Y")
        }

    @Test
    fun givenRestartAfterCatchUpWithNothingMarkedProcessed_whenCollectingLegacySyncAgain_thenShouldKeepStoredOrderAndMarkLiveAsPending() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())
            assertTrue(harness.unprocessedLiveFlags().all { isLive -> !isLive })
        }

    @Test
    fun givenRestartAfterFirstTwoEventsWereMarkedProcessed_whenCollectingLegacySyncAgain_thenShouldKeepRemainingOrder() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()
            harness.markEventsAsProcessed("A", "B")

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())
            assertTrue(harness.unprocessedLiveFlags().all { isLive -> !isLive })
        }

    @Test
    fun givenRestartWithStaleCursor_whenNotificationsEndpointReturnsDuplicates_thenShouldNotDuplicateOrReorderStoredEvents() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()
            harness.setLastSavedEventId("B")

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    pendingPages = mapOf(
                        "B" to listOf(notificationPage(listOf("C", "E", "F", "G"), hasMore = false))
                    )
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())
            assertEquals(8, harness.unprocessedEvents().size)
            assertTrue(harness.unprocessedLiveFlags().all { isLive -> !isLive })
        }

    @Test
    fun givenWsEventDuplicatesPendingPageEvent_whenCollectingLegacySync_thenShouldSkipDuplicateAndPreservePendingPosition() =
        runTest {
            val harness = newHarness()
            val repository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("D")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("E"))
                    ),
                    pendingPages = mapOf(
                        null to listOf(notificationPage(listOf("A", "B", "D"), hasMore = false))
                    )
                )
            )

            repository.collectLegacySync()

            assertEquals(listOf("A", "B", "D", "E"), harness.unprocessedEventIds())
            assertEquals(listOf(false, false, false, true), harness.unprocessedLiveFlags())
            assertLastSavedEventId(repository, "E")
        }

    @Test
    fun givenNoPendingEvents_whenCollectingLegacySync_thenShouldOnlyStoreLiveWsEvents() =
        runTest {
            val harness = newHarness()
            val repository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("X")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))
                    ),
                    pendingPages = mapOf(
                        null to listOf(emptyNotificationPage())
                    )
                )
            )

            repository.collectLegacySync()

            assertEquals(listOf("X", "Y"), harness.unprocessedEventIds())
            assertTrue(harness.unprocessedLiveFlags().all { it })
            assertLastSavedEventId(repository, "Y")
        }

    @Test
    fun givenRestartWithNewLiveWsEvents_whenCollectingLegacySyncAgain_thenShouldAppendNewEventsAfterExisting() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("W")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("V"))
                    ),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "E", "F", "G", "Z", "Y", "W", "V"), harness.unprocessedEventIds())
            val flags = harness.unprocessedLiveFlags()
            assertTrue(flags.subList(0, 8).all { !it })
            assertTrue(flags.subList(8, 10).all { it })
        }

    @Test
    fun givenRestartWithWsEventDuplicatingUnprocessedEvent_whenCollectingLegacySyncAgain_thenShouldPreserveOriginalPosition() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("C")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("W"))
                    ),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "E", "F", "G", "Z", "Y", "W"), harness.unprocessedEventIds())
            val flags = harness.unprocessedLiveFlags()
            assertTrue(flags.subList(0, 8).all { !it })
            assertTrue(flags[8])
        }

    @Test
    fun givenProcessedEventReArrivesViaWs_whenCollectingLegacySyncAgain_thenShouldNotRequeueIt() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()
            harness.markEventsAsProcessed("A", "B")

            val restartedRepository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("A"))
                    ),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            )

            restartedRepository.collectLegacySync()

            assertEquals(listOf("C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())
            assertTrue(harness.unprocessedLiveFlags().all { !it })
        }

    @Test
    fun givenMultipleRestartsWithIncrementalProcessing_whenCollectingLegacySyncRepeatedly_thenShouldPreserveRemainingOrder() =
        runTest {
            val harness = newHarness()
            harness.seedInitialCatchUpWithBufferedLiveEvents()

            // Phase 1: process A, B and restart
            harness.markEventsAsProcessed("A", "B")
            harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            ).collectLegacySync()

            assertEquals(listOf("C", "E", "F", "G", "Z", "Y"), harness.unprocessedEventIds())

            // Phase 2: process C and restart again with new WS events including duplicate F
            harness.markEventsAsProcessed("C")
            harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("F")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("W"))
                    ),
                    pendingPages = mapOf("Y" to listOf(emptyNotificationPage()))
                )
            ).collectLegacySync()

            assertEquals(listOf("E", "F", "G", "Z", "Y", "W"), harness.unprocessedEventIds())
            val flags = harness.unprocessedLiveFlags()
            assertTrue(flags.subList(0, 5).all { !it })
            assertTrue(flags[5])
        }

    @Test
    fun givenTransientEventsInPendingPageAndWs_whenCollectingLegacySync_thenLastSavedEventIdShouldReflectLastNonTransient() =
        runTest {
            val harness = newHarness()
            val repository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("T", transient = true))
                    ),
                    pendingPages = mapOf(
                        null to listOf(
                            notificationPageFromEvents(
                                events = listOf(
                                    storedEvent("A"),
                                    storedEvent("B", transient = true),
                                    storedEvent("C"),
                                    storedEvent("D", transient = true)
                                ),
                                hasMore = false
                            )
                        )
                    )
                )
            )

            repository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "D", "T"), harness.unprocessedEventIds())
            assertLastSavedEventId(repository, "C")
        }

    @Test
    fun givenSinglePendingPage_whenCollectingLegacySync_thenShouldStorePendingBeforeLive() =
        runTest {
            val harness = newHarness()
            val repository = harness.createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))
                    ),
                    pendingPages = mapOf(
                        null to listOf(notificationPage(listOf("A", "B", "C"), hasMore = false))
                    )
                )
            )

            repository.collectLegacySync()

            assertEquals(listOf("A", "B", "C", "Y"), harness.unprocessedEventIds())
            assertEquals(listOf(false, false, false, true), harness.unprocessedLiveFlags())
            assertLastSavedEventId(repository, "Y")
        }

    private fun newHarness(): IntegrationHarness {
        val database = TestUserDatabase(TestUser.ENTITY_ID, dispatcher)
        databases += database
        return IntegrationHarness(database)
    }

    private suspend fun assertLastSavedEventId(repository: EventRepository, expectedId: String) {
        val lastSavedEventId = repository.lastSavedEventId()
        assertTrue(lastSavedEventId is Either.Right)
        assertEquals(expectedId, lastSavedEventId.value)
    }

    private class IntegrationHarness(
        private val database: TestUserDatabase
    ) {
        private val clientRegistrationStorage = ClientRegistrationStorageImpl(database.builder.metadataDAO)

        fun createRepository(notificationApi: NotificationApi): EventRepository {
            return EventDataSource(
                notificationApi = notificationApi,
                metadataDAO = database.builder.metadataDAO,
                eventDAO = database.builder.eventDAO,
                currentClientId = CurrentClientIdProvider { Either.Right(TestClient.CLIENT_ID) },
                selfUserId = TestUser.SELF.id,
                clientRegistrationStorage = clientRegistrationStorage,
                restartSlowSyncProcessForRecovery = object : RestartSlowSyncProcessForRecoveryUseCase {
                    override suspend fun invoke() = Unit
                },
                logger = com.wire.kalium.common.logger.kaliumLogger
            )
        }

        suspend fun seedInitialCatchUpWithBufferedLiveEvents() {
            createRepository(
                FakeNotificationApi(
                    websocketEvents = listOf(
                        WebSocketEvent.Open(shouldProcessPendingEvents = true),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Z")),
                        WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))
                    ),
                    pendingPages = mapOf(
                        null to listOf(notificationPage(listOf("A", "B", "C"), hasMore = true)),
                        "C" to listOf(notificationPage(listOf("E", "F", "G"), hasMore = false))
                    )
                )
            ).collectLegacySync()
        }

        suspend fun unprocessedEvents(): List<EventEntity> = database.builder.eventDAO.observeUnprocessedEvents(100).first()

        suspend fun unprocessedEventIds(): List<String> = unprocessedEvents().map { it.eventId }

        suspend fun unprocessedLiveFlags(): List<Boolean> = unprocessedEvents().map { it.isLive }

        suspend fun markEventsAsProcessed(vararg eventIds: String) {
            eventIds.forEach { eventId ->
                database.builder.eventDAO.markEventAsProcessed(eventId)
            }
        }

        suspend fun setLastSavedEventId(eventId: String) {
            database.builder.metadataDAO.insertValue(eventId, LAST_SAVED_EVENT_ID_KEY)
        }
    }

    private class FakeNotificationApi(
        websocketEvents: List<WebSocketEvent<EventResponseToStore>>,
        pendingPages: Map<String?, List<NotificationResponse>>
    ) : NotificationApi {
        private val websocketChannel = Channel<WebSocketEvent<EventResponseToStore>>(Channel.UNLIMITED).apply {
            websocketEvents.forEach { trySend(it) }
            close()
        }
        private val pendingResponses = pendingPages
            .mapValues { (_, value) -> ArrayDeque(value) }
            .toMutableMap()

        override suspend fun mostRecentNotification(queryClient: String): NetworkResponse<EventResponse> =
            NetworkResponse.Success(
                EventResponse(id = "most-recent", payload = listOf(memberJoinEvent(Instant.UNIX_FIRST_DATE))),
                emptyMap(),
                200
            )

        override suspend fun notificationsByBatch(
            querySize: Int,
            queryClient: String,
            querySince: String
        ): NetworkResponse<NotificationResponse> = success(nextPendingPage(querySince))

        override suspend fun oldestNotification(queryClient: String): NetworkResponse<EventResponseToStore> =
            success(storedEvent("oldest"))

        override suspend fun getAllNotifications(querySize: Int, queryClient: String): NetworkResponse<NotificationResponse> =
            success(nextPendingPage(null))

        override suspend fun listenToLiveEvents(clientId: String): NetworkResponse<Flow<WebSocketEvent<EventResponseToStore>>> =
            success(websocketChannel.consumeAsFlow())

        override suspend fun consumeLiveEvents(
            clientId: String,
            markerId: String
        ): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> {
            throw UnsupportedOperationException("Not used in legacy notification integration tests")
        }

        override suspend fun acknowledgeEvents(
            clientId: String,
            markerId: String,
            eventAcknowledgeRequest: EventAcknowledgeRequest
        ) = Unit

        private fun nextPendingPage(querySince: String?): NotificationResponse {
            return pendingResponses[querySince]?.removeFirstOrNull() ?: emptyNotificationPage()
        }

        private fun <T : Any> success(value: T): NetworkResponse.Success<T> = NetworkResponse.Success(
            value = value,
            headers = emptyMap(),
            httpCode = 200
        )
    }

    private companion object {
        const val LAST_SAVED_EVENT_ID_KEY = "last_processed_event_id"

        fun emptyNotificationPage() = notificationPage(emptyList<String>(), hasMore = false)

        fun notificationPage(ids: List<String>, hasMore: Boolean): NotificationResponse = NotificationResponse(
            time = "2024-01-01T00:00:00Z",
            hasMore = hasMore,
            notifications = ids.map(::storedEvent)
        )

        fun notificationPageFromEvents(events: List<EventResponseToStore>, hasMore: Boolean): NotificationResponse = NotificationResponse(
            time = "2024-01-01T00:00:00Z",
            hasMore = hasMore,
            notifications = events
        )

        fun storedEvent(id: String, transient: Boolean = false): EventResponseToStore = EventResponseToStore(
            id = id,
            payload = KtxSerializer.json.encodeToString(listOf(memberJoinEvent(timestampForId(id)))),
            transient = transient
        )

        fun memberJoinEvent(time: Instant) = EventContentDTO.Conversation.MemberJoinDTO(
            TestConversation.NETWORK_ID,
            TestConversation.NETWORK_USER_ID1,
            time,
            ConversationMembers(emptyList(), emptyList()),
            TestConversation.NETWORK_USER_ID1.value
        )

        fun timestampForId(id: String): Instant {
            val offset = id.firstOrNull()?.code?.toLong() ?: 0L
            return Instant.fromEpochSeconds(offset)
        }
    }
}

private suspend fun EventRepository.collectLegacySync() {
    when (val liveEventsResult = liveEvents()) {
        is Either.Left -> error("Expected legacy sync flow to start successfully, but got $liveEventsResult")
        is Either.Right -> liveEventsResult.value.collect()
    }
}
