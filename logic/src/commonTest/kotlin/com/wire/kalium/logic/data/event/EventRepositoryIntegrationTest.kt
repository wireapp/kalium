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
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.IncrementalSyncPhase
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventAcknowledgeRequest
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.SynchronizationDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.client.ClientRegistrationStorageImpl
import com.wire.kalium.persistence.dao.event.EventEntity
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.NewEventEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

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

    @Test
    fun nseStoredPageResumesWithoutFetchingOrEmittingCompletedEvents() = runTest {
        for (completed in listOf(listOf("A"), listOf("A", "B", "C"))) {
            val harness = newHarness()
            // Use the NSE writer, including its atomic fetched-page cursor.
            harness.storeNsePage(listOf("A", "B", "C"))
            harness.markEventsAsProcessed(*completed.toTypedArray())
            val api = FakeNotificationApi(
                listOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                mapOf("C" to listOf(emptyNotificationPage()))
            )
            val repository = harness.createRepository(api)
            repository.collectLegacySync()
            assertEquals(listOf<String?>("C"), api.requestedCursors)
            assertLastSavedEventId(repository, "C")
            assertEquals(listOf("A", "B", "C") - completed.toSet(), harness.unprocessedEventIds())
        }
    }

    private fun newHarness(): IntegrationHarness {
        val database = TestUserDatabase(TestUser.ENTITY_ID, dispatcher)
        databases += database
        return IntegrationHarness(database)
    }

    @Test
    fun pageStorageFailureStopsCatchUpBeforeReadinessAndRetriesFromTheCommittedCursor() = failedPage(false)

    @Test
    fun pageCancellationEscapesWithoutAnnouncingSuccessfulCatchUp() = failedPage(true)

    private fun failedPage(cancel: Boolean) = runTest {
        val harness = newHarness()
        val api = FakeNotificationApi(
            listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))),
            mapOf(null to listOf(notificationPage(listOf("A", "B"), true)), "B" to listOf(notificationPage(listOf("C", "D"), true)))
        )
        val real = harness.eventDAO
        val failing = object : EventDAO by real {
            override suspend fun storeFetchedPage(events: List<NewEventEntity>) {
                if (events.any { it.eventId == "C" }) {
                    if (cancel) throw CancellationException("Interrupted page")
                    error("Unavailable page storage")
                }
                real.storeFetchedPage(events)
            }
        }
        val repository = harness.createRepository(api, failing)
        val phases = mutableListOf<IncrementalSyncPhase>()
        val flow = assertIs<Either.Right<Flow<IncrementalSyncPhase>>>(repository.liveEvents()).value
        if (cancel) assertFailsWith<CancellationException> { flow.collect { phases += it } }
        else assertFailsWith<KaliumSyncException> { flow.collect { phases += it } }
        assertTrue(phases.isEmpty())
        assertEquals(listOf(null, "B"), api.requestedCursors)
        assertEquals(listOf("A", "B"), harness.unprocessedEventIds())
        assertLastSavedEventId(repository, "B")
        val retry = FakeNotificationApi(listOf(WebSocketEvent.Open()), mapOf("B" to listOf(notificationPage(listOf("C", "D"), false))))
        val reopened = harness.createRepository(retry)
        reopened.collectLegacySync()
        assertEquals(listOf<String?>("B"), retry.requestedCursors)
        assertEquals(listOf("A", "B", "C", "D"), harness.unprocessedEventIds())
        assertLastSavedEventId(reopened, "D")
    }

    @Test
    fun pendingPreparationFailurePreventsRESTFetchingAndReadiness() = runTest {
        val harness = newHarness()
        val api = FakeNotificationApi(listOf(WebSocketEvent.Open()), emptyMap())
        val failing = object : EventDAO by harness.eventDAO {
            override suspend fun setAllUnprocessedEventsAsPending() = error("Unavailable pending storage")
        }
        val repository = harness.createRepository(api, failing)
        assertFailsWith<KaliumSyncException> { repository.collectLegacySync() }
        assertTrue(api.requestedCursors.isEmpty())
        assertTrue(harness.unprocessedEventIds().isEmpty())
    }

    @Test
    fun transientTailDrivesPaginationWhileOnlyDurableEventAdvancesRestartCursor() = runTest {
        val harness = newHarness()
        val api = FakeNotificationApi(listOf(WebSocketEvent.Open()), mapOf(
            null to listOf(notificationPageFromEvents(listOf(storedEvent("A"), storedEvent("T", transient = true)), true)),
            "T" to listOf(notificationPage(listOf("B"), false))
        ))
        val repository = harness.createRepository(api)
        repository.collectLegacySync()
        assertEquals(listOf(null, "T"), api.requestedCursors)
        assertEquals(listOf("A", "T", "B"), harness.unprocessedEventIds())
        assertLastSavedEventId(repository, "B")
    }

    @Test
    fun emptyNonterminalPageCannotLoopOrAnnounceReadiness() = runTest {
        val harness = newHarness()
        val api = FakeNotificationApi(listOf(WebSocketEvent.Open()), mapOf(null to listOf(notificationPage(emptyList(), true))))
        assertFailsWith<KaliumSyncException> { harness.createRepository(api).collectLegacySync() }
        assertEquals(listOf<String?>(null), api.requestedCursors)
        assertTrue(harness.unprocessedEventIds().isEmpty())
    }

    @Test
    fun firstLiveEventAfterOpenDeletesProcessedHistory() = runTest {
        val harness = newHarness()
        harness.storeNsePage(listOf("A", "B", "C"))
        harness.markEventsAsProcessed("A", "B")
        val api = FakeNotificationApi(
            listOf(WebSocketEvent.Open(), WebSocketEvent.BinaryPayloadReceived(storedEvent("Y"))),
            mapOf("C" to listOf(emptyNotificationPage()))
        )
        harness.createRepository(api).collectLegacySync()
        assertEquals(listOf("C", "Y"), harness.allEventIds())
    }

    @Test
    fun consumableEventsAreStoredBeforeAcknowledgementAndBecomeLiveAtTheSessionMarker() = runTest {
        val harness = newHarness()
        val acks = mutableListOf<Pair<ULong?, List<String>>>()
        val ackMarkers = mutableListOf<String>()
        var sessionMarker = ""
        val api = FakeNotificationApi(emptyList(), emptyMap(), consumableEvents = { marker ->
            sessionMarker = marker
            listOf(
                WebSocketEvent.Open(shouldProcessPendingEvents = false),
                consumableEvent("A", deliveryTag = 1UL),
                WebSocketEvent.BinaryPayloadReceived(
                    ConsumableNotificationResponse.SynchronizationNotification(SynchronizationDataDTO(2UL, "foreign"))
                ),
                WebSocketEvent.BinaryPayloadReceived(
                    ConsumableNotificationResponse.SynchronizationNotification(SynchronizationDataDTO(3UL, marker))
                ),
                consumableEvent("B", deliveryTag = 4UL),
            )
        }, onAck = { marker, request ->
            ackMarkers += marker
            acks += request.data?.deliveryTag to harness.allEventIds()
        })
        val repository = harness.createRepository(api, hasConsumableNotifications = true)

        val phases = repository.collectPhases()

        assertEquals(
            listOf(
                IncrementalSyncPhase.CatchingUp,
                IncrementalSyncPhase.CatchingUp,
                IncrementalSyncPhase.ReadyToProcess,
                IncrementalSyncPhase.ReadyToProcess,
            ),
            phases
        )
        assertEquals(listOf("A", "B"), harness.unprocessedEventIds())
        assertEquals(listOf(false, true), harness.unprocessedLiveFlags())
        assertEquals<List<Pair<ULong?, List<String>>>>(
            listOf(1UL to listOf("A"), 2UL to listOf("A"), 3UL to listOf("A"), 4UL to listOf("A", "B")),
            acks
        )
        // The connection's own marker until the backend confirms it, then none.
        assertEquals(listOf(sessionMarker, sessionMarker, sessionMarker, ""), ackMarkers)
        assertLastSavedEventId(repository, "B")
        repository.acknowledgeMissedEvent()
        assertEquals("", ackMarkers.last())
    }

    @Test
    fun missedConsumableNotificationAcknowledgesFullSyncAndRestartsSlowSync() = runTest {
        val harness = newHarness()
        val recovery = mutableListOf<String>()
        var sessionMarker = ""
        val api = FakeNotificationApi(emptyList(), emptyMap(), consumableEvents = { marker ->
            sessionMarker = marker
            listOf(
                WebSocketEvent.Open(shouldProcessPendingEvents = false),
                WebSocketEvent.BinaryPayloadReceived(ConsumableNotificationResponse.MissedNotification),
            )
        }, onAck = { marker, request -> recovery += "${request.type}:${marker == sessionMarker}" })

        harness.createRepository(api, hasConsumableNotifications = true, restartSlowSync = { recovery += "restart" }).collectPhases()

        // Acknowledge the gap with this connection's marker before restarting slow sync.
        assertEquals(listOf("${AcknowledgeType.ACK_FULL_SYNC}:true", "restart"), recovery)
        assertTrue(harness.allEventIds().isEmpty())
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

        val eventDAO: EventDAO get() = database.builder.eventDAO

        fun createRepository(
            notificationApi: NotificationApi,
            events: EventDAO = eventDAO,
            hasConsumableNotifications: Boolean = false,
            restartSlowSync: () -> Unit = {},
        ): EventRepository {
            return EventDataSource(
                notificationApi = notificationApi,
                metadataDAO = database.builder.metadataDAO,
                eventDAO = events,
                currentClientId = CurrentClientIdProvider { Either.Right(TestClient.CLIENT_ID) },
                selfUserId = TestUser.SELF.id,
                clientRegistrationStorage = object : ClientRegistrationStorage by clientRegistrationStorage {
                    override suspend fun observeHasConsumableNotifications() = flowOf(hasConsumableNotifications)
                },
                restartSlowSyncProcessForRecovery = object : RestartSlowSyncProcessForRecoveryUseCase {
                    override suspend fun invoke() = restartSlowSync()
                },
                logger = com.wire.kalium.common.logger.kaliumLogger
            )
        }

        suspend fun allEventIds(): List<String> = database.builder.eventDAO.observeEvents(0).first().map { it.eventId }

        suspend fun storeNsePage(ids: List<String>) {
            database.builder.notificationEventStore.storePage(null, ids.map { id ->
                val event = storedEvent(id)
                NewEventEntity(event.id, event.payload, event.transient, false)
            })
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
        pendingPages: Map<String?, List<NotificationResponse>>,
        private val consumableEvents: (marker: String) -> List<WebSocketEvent<ConsumableNotificationResponse>> = { emptyList() },
        private val onAck: suspend (markerId: String, EventAcknowledgeRequest) -> Unit = { _, _ -> },
    ) : NotificationApi {
        val requestedCursors = mutableListOf<String?>()
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
        ): NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>> = success(consumableEvents(markerId).asFlow())

        override suspend fun acknowledgeEvents(
            clientId: String,
            markerId: String,
            eventAcknowledgeRequest: EventAcknowledgeRequest
        ) = onAck(markerId, eventAcknowledgeRequest)

        private fun nextPendingPage(querySince: String?): NotificationResponse {
            requestedCursors += querySince
            return checkNotNull(pendingResponses[querySince]?.removeFirstOrNull()) { "Unexpected notification cursor" }
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

        fun consumableEvent(id: String, deliveryTag: ULong) = WebSocketEvent.BinaryPayloadReceived<ConsumableNotificationResponse>(
            ConsumableNotificationResponse.EventNotification(EventDataDTO(deliveryTag, storedEvent(id)))
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

private suspend fun EventRepository.collectPhases(): List<IncrementalSyncPhase> {
    val phases = mutableListOf<IncrementalSyncPhase>()
    when (val liveEventsResult = liveEvents()) {
        is Either.Left -> error("Expected sync flow to start successfully, but got $liveEventsResult")
        is Either.Right -> liveEventsResult.value.collect { phases += it }
    }
    return phases
}

private suspend fun EventRepository.collectLegacySync() {
    when (val liveEventsResult = liveEvents()) {
        is Either.Left -> error("Expected legacy sync flow to start successfully, but got $liveEventsResult")
        is Either.Right -> liveEventsResult.value.collect()
    }
}
