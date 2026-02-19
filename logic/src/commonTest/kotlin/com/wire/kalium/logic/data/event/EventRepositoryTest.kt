/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import app.cash.turbine.test
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.slow.RestartSlowSyncProcessForRecoveryUseCase
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.EventResponseToStore
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.authenticated.notification.SynchronizationDataDTO
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.client.ClientRegistrationStorage
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.event.EventDAO
import com.wire.kalium.persistence.dao.event.EventEntity
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.ktor.http.HttpStatusCode
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class EventRepositoryTest {

    @Test
    fun givenLiveEvents_whenGettingLiveEvents_thenReturnFromListenLiveEvents() = runTest {
        val (arrangement, eventRepository) = Arrangement()
            .withClientHasConsumableNotifications(hasConsumableNotifications = false)
            .withLastStoredEventId("someNotificationId")
            .withListenLiveEventsReturning(NetworkResponse.Success(flowOf(), mapOf(), 200))
            .arrange()

        eventRepository.liveEvents()
        coVerify { arrangement.notificationApi.listenToLiveEvents(eq(TestClient.CLIENT_ID.value)) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenLiveEvents_whenGettingLiveEventsWithConsumableNotifications_thenReturnFromNewApiConsumeLiveEvents() = runTest {
        val (arrangement, eventRepository) = Arrangement()
            .withClientHasConsumableNotifications(hasConsumableNotifications = true)
            .withLastStoredEventId("someNotificationId")
            .withConsumeLiveEventsReturning(NetworkResponse.Success(flowOf(), mapOf(), 200))
            .arrange()

        eventRepository.liveEvents()
        coVerify { arrangement.notificationApi.consumeLiveEvents(eq(TestClient.CLIENT_ID.value), any()) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASavedLastSavedId_whenGettingLastEventId_thenShouldReturnIt() = runTest {
        val eventId = "dh817h2e"

        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId(eventId)
            .arrange()

        val result = eventRepository.lastSavedEventId()
        result.shouldSucceed { assertEquals(eventId, it) }
    }

    @Test
    fun givenClientId_whenFetchingOldestEventId_thenShouldPassCorrectIdToAPI() = runTest {
        val currentClientId = ClientId("testClientId")
        val (arrangement, eventRepository) = Arrangement()
            .withCurrentClientIdReturning(currentClientId)
            .withOldestNotificationReturning(NetworkResponse.Error(KaliumException.NoNetwork()))
            .arrange()

        eventRepository.fetchOldestAvailableEventId()

        coVerify {
            arrangement.notificationApi.oldestNotification(eq(currentClientId.value))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAPIFailure_whenFetchingOldestEventId_thenShouldPropagateFailure() = runTest {
        val (_, eventRepository) = Arrangement()
            .withOldestNotificationReturning(NetworkResponse.Error(KaliumException.NoNetwork()))
            .arrange()

        eventRepository.fetchOldestAvailableEventId()
            .shouldFail {
                assertIs<NetworkFailure.NoNetworkConnection>(it)
            }
    }

    @Test
    fun givenAPISucceeds_whenFetchingOldestEventId_thenShouldPropagateEventId() = runTest {
        val eventId = "testEventId"
        val result = NetworkResponse.Success(
            value = EventResponseToStore(eventId, "[]"),
            headers = mapOf(),
            httpCode = HttpStatusCode.OK.value
        )
        val (_, eventRepository) = Arrangement()
            .withOldestNotificationReturning(result)
            .arrange()

        eventRepository.fetchOldestAvailableEventId()
            .shouldSucceed {
                assertEquals(eventId, it)
            }
    }

    @Test
    fun givenLiveEvent_whenReceived_thenShouldAcknowledgeWithACK() = runTest {
        val eventId = "event-id"
        val testEventResponse = EventResponseToStore(
            id = eventId,
            payload = KtxSerializer.json.encodeToString(listOf(MEMBER_JOIN_EVENT))
        )
        val deliveryTag = 987654UL

        val (arrangement, repository) = Arrangement()
            .withCurrentClientIdReturning(TestClient.CLIENT_ID)
            .withClientHasConsumableNotifications(true)
            .withClearProcessedEvents(eventId)
            .withConsumeLiveEventsReturning(
                NetworkResponse.Success(
                    value = flowOf(
                        WebSocketEvent.BinaryPayloadReceived(
                            ConsumableNotificationResponse.EventNotification(
                                EventDataDTO(
                                    deliveryTag = deliveryTag,
                                    event = testEventResponse
                                )
                            )
                        )
                    ),
                    headers = mapOf(),
                    httpCode = 200
                )
            )
            .withAcknowledgeEvents()
            .arrange()

        val result = repository.liveEvents()
        result.shouldSucceed {}

        result.fold({}, { flow ->
            flow.test {
                awaitItem()
                awaitComplete()
                coVerify {
                    arrangement.notificationApi.acknowledgeEvents(
                        eq(TestClient.CLIENT_ID.value),
                        any(),
                        matches {
                            it.type == AcknowledgeType.ACK &&
                                    it.data?.deliveryTag == deliveryTag
                            it.data?.multiple == false
                        }
                    )
                }.wasInvoked(exactly = once)
            }
        })
    }

    @Test
    fun givenUnprocessedEventsInDAO_whenObservingEvents_thenShouldEmitMappedEvents() = runTest {
        val testEvent = EventResponse(
            id = "test-event-id",
            payload = listOf(MEMBER_JOIN_EVENT)
        )
        val testPayload = KtxSerializer.json.encodeToString(testEvent.payload)

        val testEventEntity = EventEntity(
            id = 1L,
            eventId = testEvent.id,
            isProcessed = false,
            payload = testPayload,
            isLive = true,
            transient = testEvent.transient
        )

        val (_, repository) = Arrangement()
            .withLastStoredEventId(null)
            .withUnprocessedEvents(flowOf(listOf(testEventEntity)))
            .arrange()

        repository.observeEvents().test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(testEvent.id, emitted.first().event.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastEmittedIdExistsInBatch_whenObservingEvents_thenShouldFilterCorrectly() = runTest {
        val eventA = EventResponse(id = "a", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 0)
        val eventB = EventResponse(id = "b", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 1)
        val eventC = EventResponse(id = "c", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 2)

        val unprocessedEventsChannel = Channel<List<EventEntity>>(capacity = Channel.UNLIMITED)

        val entities = listOf(eventA, eventB, eventC)

        val (arrangement, repository) = Arrangement()
            .withUnprocessedEvents(unprocessedEventsChannel.consumeAsFlow())
            .arrange()

        repository.observeEvents().test {
            unprocessedEventsChannel.send(listOf(eventA)) // set last emitted id to "a"
            awaitItem() // ignore emitted "a" event as it's just to set the initial conditions for this test

            unprocessedEventsChannel.send(entities) // send batch that contains "a", so "a" should be filtered and only "b" and "c" emitted
            val secondEmitted = awaitItem()
            assertEquals(listOf("b", "c"), secondEmitted.map { it.event.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastEmittedIdNotInBatch_whenObservingEvents_thenShouldNotFilterAnything() = runTest {
        val eventA = EventResponse(id = "a", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 0)
        val eventX = EventResponse(id = "x", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 1)
        val eventY = EventResponse(id = "y", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 2)
        val eventZ = EventResponse(id = "z", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(id = 3)

        val unprocessedEventsChannel = Channel<List<EventEntity>>(capacity = Channel.UNLIMITED)

        val entities = listOf(eventX, eventY, eventZ)

        val (arrangement, repository) = Arrangement()
            .withUnprocessedEvents(unprocessedEventsChannel.consumeAsFlow())
            .arrange()

        repository.observeEvents().test {
            unprocessedEventsChannel.send(listOf(eventA)) // set last emitted id to "a"
            awaitItem() // ignore emitted "a" event as it's just to set the initial conditions for this test

            unprocessedEventsChannel.send(entities) // send batch that doesn't contain "a", so no filtering and all should be emitted
            val emitted = awaitItem()
            assertEquals(listOf("x", "y", "z"), emitted.map { it.event.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLiveEventForSyncMarker_whenReceived_thenShouldAcknowledgeWithACK() = runTest {
        val eventId = "event-id"
        val deliveryTag = 987654UL

        val (arrangement, repository) = Arrangement()
            .withCurrentClientIdReturning(TestClient.CLIENT_ID)
            .withClientHasConsumableNotifications(true)
            .withClearProcessedEvents(eventId)
            .withConsumeLiveEventsReturning(
                NetworkResponse.Success(
                    value = flowOf(
                        WebSocketEvent.BinaryPayloadReceived(
                            ConsumableNotificationResponse.SynchronizationNotification(
                                SynchronizationDataDTO(
                                    deliveryTag = deliveryTag,
                                    markerId = "sync-marker"
                                )
                            )
                        )
                    ),
                    headers = mapOf(),
                    httpCode = 200
                )
            )
            .withAcknowledgeEvents()
            .arrange()

        val result = repository.liveEvents()
        result.shouldSucceed {}

        result.fold({}, { flow ->
            flow.test {
                awaitComplete()
                coVerify {
                    arrangement.notificationApi.acknowledgeEvents(
                        eq(TestClient.CLIENT_ID.value),
                        any(),
                        matches {
                            it.type == AcknowledgeType.ACK &&
                                    it.data?.deliveryTag == deliveryTag
                            it.data?.multiple == false
                        }
                    )
                }.wasInvoked(exactly = once)
            }
        })
    }

    @Test
    fun givenNotFoundFailure_whenReceivingLiveEvent_thenShouldThrowSyncEventOrClientNotFound() = runTest {
        val (_, repository) = Arrangement()
            .withClientHasConsumableNotifications(false)
            .withCurrentClientIdReturning(TestClient.CLIENT_ID)
            .withSetAllUnprocessedEventsAsPending()
            .withLastStoredEventId("someNotificationId")
            .withListenLiveEventsReturning(
                NetworkResponse.Success(
                    flowOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    emptyMap(),
                    200
                )
            )
            .apply {
                coEvery {
                    notificationApi.notificationsByBatch(any(), any(), any())
                } returns NetworkResponse.Error(TestNetworkException.notFound)
            }
            .arrange()

        val eitherFlow = repository.liveEvents()
        assertTrue(eitherFlow is Either.Right)

        val flow = eitherFlow.value

        val thrown = assertFailsWith<KaliumSyncException> {
            flow.collect {}
        }

        assertTrue(thrown.coreFailureCause is CoreFailure.SyncEventOrClientNotFound)

    }

    @Test
    fun givenGenericServerFailure_whenReceivingLiveEvent_thenShouldThrowOriginalCoreFailure() = runTest {
        val (_, repository) = Arrangement()
            .withClientHasConsumableNotifications(false)
            .withCurrentClientIdReturning(TestClient.CLIENT_ID)
            .withSetAllUnprocessedEventsAsPending()
            .withLastStoredEventId("someNotificationId")
            .withListenLiveEventsReturning(
                NetworkResponse.Success(
                    flowOf(WebSocketEvent.Open(shouldProcessPendingEvents = true)),
                    emptyMap(),
                    200
                )
            )
            .apply {
                coEvery {
                    notificationApi.notificationsByBatch(any(), any(), any())
                } returns NetworkResponse.Error(TestNetworkException.generic)
            }
            .arrange()

        val eitherFlow = repository.liveEvents()
        assertTrue(eitherFlow is Either.Right)

        val flow = eitherFlow.value

        val thrown = assertFailsWith<KaliumSyncException> {
            flow.collect {}
        }

        assertFalse(thrown.coreFailureCause is CoreFailure.SyncEventOrClientNotFound)
        val cause = thrown.coreFailureCause
        assertIs<NetworkFailure.ServerMiscommunication>(cause)

        val actual = cause.rootCause
        assertIs<KaliumException.InvalidRequestError>(actual)

        assertEquals(400, actual.errorResponse.code)
        assertEquals("generic test error", actual.errorResponse.message)
        assertEquals("generic-test-error", actual.errorResponse.label)
    }

    @Test
    fun givenEventsPreviouslyEmitted_whenEmittingSameEventsAgain_thenTheyAreFilteredCorrectly() = runTest {
        val eventA = EventResponse(id = "a", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(0)
        val eventB = EventResponse(id = "b", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(1)
        val eventC = EventResponse(id = "c", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(2)

        val initialEntities = listOf(eventA, eventB, eventC)
        val repeatEntities = listOf(eventA, eventB, eventC).map { it.copy(id = it.id + 10) }

        val channel = Channel<List<EventEntity>>(Channel.UNLIMITED)

        val (_, repository) = Arrangement()
            .withUnprocessedEvents(channel.consumeAsFlow())
            .arrange()

        repository.observeEvents().test {
            channel.send(initialEntities) // send initial batch of events
            val first = awaitItem()
            assertEquals(listOf("a", "b", "c"), first.map { it.event.id }) // all events emitted at first

            channel.send(repeatEntities) // repeat sending batch with the same events
            expectNoEvents() // no new events should be emitted, even empty list, since the whole batch is the same

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenLastEmittedEventIsSecond_whenReceivingThreeEvents_thenShouldEmitOnlyLast() = runTest {
        val eventA = EventResponse(id = "a", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(0)
        val eventB = EventResponse(id = "b", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(1)
        val eventC = EventResponse(id = "c", payload = listOf(MEMBER_JOIN_EVENT)).toEventEntity(2)

        val allEntities = listOf(eventA, eventB, eventC)

        val channel = Channel<List<EventEntity>>(Channel.UNLIMITED)

        val (arrangement, repository) = Arrangement()
            .withUnprocessedEvents(channel.consumeAsFlow())
            .arrange()

        repository.observeEvents().test {
            val abEvents = allEntities.take(2)

            channel.send(abEvents) // send first two events
            val first = awaitItem()
            assertEquals(listOf("a", "b"), first.map { it.event.id }) // "a" and "b" emitted, last emitted event is now "b"

            channel.send(allEntities) // send all three events, "a" and "b" is sent again and "c" for the first time
            val second = awaitItem()

            assertEquals(listOf("c"), second.map { it.event.id }) // only "c" should be emitted, duplicated "a" and "b" should be filtered

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUnknownEventWithoutType_whenObservingEvents_thenEmitsUnknownEnvelope() = runTest {
        val eventId = "unknown-no-type"
        val entity = EventEntity(
            id = 1L,
            eventId = eventId,
            isProcessed = false,
            payload = "[{}]",
            isLive = true,
            transient = false
        )

        val (_, repository) = Arrangement()
            .withLastStoredEventId(null)
            .withUnprocessedEvents(flowOf(listOf(entity)))
            .arrange()

        repository.observeEvents().test {
            val envelopes = awaitItem()
            assertEquals(1, envelopes.size)

            val envelope = envelopes.first()
            assertEquals(eventId, envelope.event.id)
            assertEquals(EventSource.LIVE, envelope.deliveryInfo.source)

            val unknown = assertIs<Event.Unknown>(envelope.event)
            assertTrue(unknown.unknownType.isNullOrEmpty(), "Expected empty unknownType for missing 'type'")

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenUnknownEventWithUnrecognizedType_whenObservingEvents_thenEmitsUnknownEnvelopeWithType() = runTest {
        val eventId = "unknown-with-type"
        val entity = EventEntity(
            id = 2L,
            eventId = eventId,
            isProcessed = false,
            payload = """[{"type":"really-unknown","some":"data"}]""",
            isLive = true,
            transient = false
        )

        val (_, repository) = Arrangement()
            .withLastStoredEventId(null)
            .withUnprocessedEvents(flowOf(listOf(entity)))
            .arrange()

        repository.observeEvents().test {
            val envelopes = awaitItem()
            assertEquals(1, envelopes.size)

            val envelope = envelopes.first()
            assertEquals(eventId, envelope.event.id)
            assertEquals(EventSource.LIVE, envelope.deliveryInfo.source)

            val unknown = assertIs<Event.Unknown>(envelope.event)
            assertEquals("really-unknown", unknown.unknownType)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenWebSocketClosedWithNullCause_whenHandlingClosure_thenShouldThrowKaliumSyncException() = runTest {
        val (_, repository) = Arrangement()
            .withClientHasConsumableNotifications(true)
            .withCurrentClientIdReturning(TestClient.CLIENT_ID)
            .withConsumeLiveEventsReturning(
                NetworkResponse.Success(
                    value = flowOf(WebSocketEvent.Close(cause = null)),
                    headers = emptyMap(),
                    httpCode = 200
                )
            )
            .arrange()

        val eitherFlow = repository.liveEvents()
        assertTrue(eitherFlow is Either.Right)

        val flow = eitherFlow.value

        val thrown = assertFailsWith<KaliumSyncException> {
            flow.collect {}
        }

        assertIs<CoreFailure.Unknown>(thrown.coreFailureCause)
    }

    private companion object {
        const val LAST_SAVED_EVENT_ID_KEY = "last_processed_event_id"
        val MEMBER_JOIN_EVENT = EventContentDTO.Conversation.MemberJoinDTO(
            TestConversation.NETWORK_ID,
            TestConversation.NETWORK_USER_ID1,
            Instant.UNIX_FIRST_DATE,
            ConversationMembers(emptyList(), emptyList()),
            TestConversation.NETWORK_USER_ID1.value
        )
    }

    private class Arrangement {

        val notificationApi: NotificationApi = mock(NotificationApi::class)
        val metaDAO = mock(MetadataDAO::class)
        val clientRegistrationStorage = mock(ClientRegistrationStorage::class)
        val clientIdProvider = mock(CurrentClientIdProvider::class)
        val eventDAO: EventDAO = mock(EventDAO::class)
        val restartSlowSyncProcessForRecoveryUseCase: RestartSlowSyncProcessForRecoveryUseCase =
            mock(RestartSlowSyncProcessForRecoveryUseCase::class)

        private val eventRepository: EventRepository = EventDataSource(
            notificationApi = notificationApi,
            metadataDAO = metaDAO,
            eventDAO = eventDAO,
            currentClientId = clientIdProvider,
            selfUserId = TestUser.SELF.id,
            clientRegistrationStorage = clientRegistrationStorage,
            restartSlowSyncProcessForRecovery = restartSlowSyncProcessForRecoveryUseCase,
            logger = kaliumLogger
        )

        init {
            runBlocking {
                withCurrentClientIdReturning(TestClient.CLIENT_ID)
                withClientHasConsumableNotifications()
                withMarkEventAsProcessed()
            }
        }

        suspend fun withClientHasConsumableNotifications(hasConsumableNotifications: Boolean = false) = apply {
            coEvery {
                clientRegistrationStorage.observeHasConsumableNotifications()
            }.returns(flowOf(hasConsumableNotifications))
        }

        suspend fun withLastStoredEventId(value: String?) = apply {
            coEvery {
                metaDAO.valueByKey(LAST_SAVED_EVENT_ID_KEY)
            }.returns(value)
        }

        suspend fun withNotificationsByBatch(result: NetworkResponse<NotificationResponse>) = apply {
            coEvery {
                notificationApi.notificationsByBatch(any(), any(), any())
            }.returns(result)
        }

        suspend fun withOldestNotificationReturning(result: NetworkResponse<EventResponseToStore>) = apply {
            coEvery {
                notificationApi.oldestNotification(any())
            }.returns(result)
        }

        suspend fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            coEvery {
                clientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withConsumeLiveEventsReturning(result: NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>>) = apply {
            coEvery {
                notificationApi.consumeLiveEvents(any(), any())
            }.returns(result)
        }

        suspend fun withListenLiveEventsReturning(result: NetworkResponse<Flow<WebSocketEvent<EventResponseToStore>>>) = apply {
            coEvery {
                notificationApi.listenToLiveEvents(any())
            }.returns(result)
        }

        suspend fun withAcknowledgeEvents() = apply {
            coEvery {
                notificationApi.acknowledgeEvents(any(), any(), any())
            }.returns(Unit)
        }

        suspend fun withUnprocessedEvents(events: Flow<List<EventEntity>>) = apply {
            coEvery {
                eventDAO.observeUnprocessedEvents(any())
            }.returns(events)
        }

        suspend fun withMarkEventAsProcessed() = apply {
            coEvery {
                eventDAO.markEventAsProcessed(any())
            }.returns(Unit)
        }

        suspend fun withSetAllUnprocessedEventsAsPending() = apply {
            coEvery {
                eventDAO.setAllUnprocessedEventsAsPending()
            }.returns(Unit)
        }

        suspend fun withClearProcessedEvents(eventId: String, id: Long = 1L, transient: Boolean = false) = apply {
            coEvery { eventDAO.getEventById(eq(eventId)) }.returns(
                EventEntity(
                    id = id,
                    eventId = eventId,
                    isProcessed = false,
                    payload = "",
                    isLive = true,
                    transient = transient
                )
            )

            coEvery { eventDAO.deleteProcessedEventsBefore(id) }.returns(Unit)
            coEvery { eventDAO.deleteAllProcessedEvents() }.returns(Unit)
        }

        inline fun arrange(): Pair<Arrangement, EventRepository> {
            return this to eventRepository
        }
    }

    private fun EventResponse.toEventEntity(
        id: Long,
        isProcessed: Boolean = false,
        isLive: Boolean = true,
        transient: Boolean = false
    ): EventEntity = EventEntity(
        id = id,
        eventId = this.id,
        isProcessed = isProcessed,
        payload = KtxSerializer.json.encodeToString(this.payload),
        isLive = isLive,
        transient = transient
    )
}
