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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.ADD_MEMBER_TO_CONVERSATION_SUCCESSFUL_RESPONSE
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestEvent.wrapAsyncInEnvelope
import com.wire.kalium.logic.framework.TestEvent.wrapInEnvelope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembers
import com.wire.kalium.network.api.authenticated.notification.AcknowledgeType
import com.wire.kalium.network.api.authenticated.notification.ConsumableNotificationResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventDataDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.WebSocketEvent
import com.wire.kalium.network.api.model.UserId
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
        coVerify { arrangement.notificationApi.consumeLiveEvents(eq(TestClient.CLIENT_ID.value)) }
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASavedLastProcessedId_whenGettingLastEventId_thenShouldReturnIt() = runTest {
        val eventId = "dh817h2e"

        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId(eventId)
            .arrange()

        val result = eventRepository.lastProcessedEventId()
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
            value = EventResponse(eventId, emptyList()),
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
    fun givenAPIFailure_whenFetchingServerTime_thenReturnNull() = runTest {
        val (_, eventRepository) = Arrangement()
            .withGetServerTimeReturning(NetworkResponse.Error(KaliumException.NoNetwork()))
            .arrange()

        val result = eventRepository.fetchServerTime()

        assertNull(result)
    }

    @Test
    fun givenLiveEvent_whenReceived_thenShouldAcknowledgeWithACK() = runTest {
        val eventId = "event-id"
        val testEventResponse = EventResponse(
            id = eventId,
            payload = listOf(MEMBER_JOIN_EVENT)
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
    fun givenAPISucceeds_whenFetchingServerTime_thenReturnTime() = runTest {
        val result = NetworkResponse.Success(
            value = "123434545",
            headers = mapOf(),
            httpCode = HttpStatusCode.OK.value
        )
        val (_, eventRepository) = Arrangement()
            .withGetServerTimeReturning(result)
            .arrange()

        val time = eventRepository.fetchServerTime()

        assertNotNull(time)
    }

    @Test
    fun givenUnprocessedEventsInDAO_whenObservingEvents_thenShouldEmitMappedEvents() = runTest {
        val testEvent = EventResponse(
            id = "test-event-id",
            payload = listOf(EventContentDTO.AsyncMissedNotification)
        )
        val testPayload = KtxSerializer.json.encodeToString(testEvent)

        val testEventEntity = EventEntity(
            id = 1L,
            eventId = testEvent.id,
            isProcessed = false,
            payload = testPayload
        )

        val (_, repository) = Arrangement()
            .withLastStoredEventId(null)
            .withUnprocessedEvents(listOf(testEventEntity))
            .arrange()

        repository.observeEvents().test {
            val emitted = awaitItem()
            assertEquals(1, emitted.size)
            assertEquals(testEvent.id, emitted.first().event.id)
            cancelAndIgnoreRemainingEvents()
        }
    }


    private companion object {
        const val LAST_PROCESSED_EVENT_ID_KEY = "last_processed_event_id"
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

        @Mock
        val eventDAO: EventDAO = mock(EventDAO::class)

        private val eventRepository: EventRepository = EventDataSource(
            notificationApi,
            metaDAO,
            eventDAO,
            clientIdProvider,
            TestUser.SELF.id,
            clientRegistrationStorage
        )

        init {
            runBlocking {
                withCurrentClientIdReturning(TestClient.CLIENT_ID)
                withClientHasConsumableNotifications()
            }
        }

        suspend fun withClientHasConsumableNotifications(hasConsumableNotifications: Boolean = false) = apply {
            coEvery {
                clientRegistrationStorage.observeHasConsumableNotifications()
            }.returns(flowOf(hasConsumableNotifications))
        }

        suspend fun withLastStoredEventId(value: String?) = apply {
            coEvery {
                metaDAO.valueByKey(LAST_PROCESSED_EVENT_ID_KEY)
            }.returns(value)
        }

        suspend fun withNotificationsByBatch(result: NetworkResponse<NotificationResponse>) = apply {
            coEvery {
                notificationApi.notificationsByBatch(any(), any(), any())
            }.returns(result)
        }

        suspend fun withOldestNotificationReturning(result: NetworkResponse<EventResponse>) = apply {
            coEvery {
                notificationApi.oldestNotification(any())
            }.returns(result)
        }

        suspend fun withGetServerTimeReturning(result: NetworkResponse<String>) = apply {
            coEvery {
                notificationApi.getServerTime(any())
            }.returns(result)
        }

        suspend fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            coEvery {
                clientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        suspend fun withConsumeLiveEventsReturning(result: NetworkResponse<Flow<WebSocketEvent<ConsumableNotificationResponse>>>) = apply {
            coEvery {
                notificationApi.consumeLiveEvents(any())
            }.returns(result)
        }

        suspend fun withListenLiveEventsReturning(result: NetworkResponse<Flow<WebSocketEvent<EventResponse>>>) = apply {
            coEvery {
                notificationApi.listenToLiveEvents(any())
            }.returns(result)
        }

        suspend fun withAcknowledgeEvents() = apply {
            coEvery {
                notificationApi.acknowledgeEvents(any(), any())
            }.returns(Unit)
        }

        suspend fun withUnprocessedEvents(events: List<EventEntity>) = apply {
            coEvery {
                eventDAO.observeUnprocessedEvents()
            }.returns(flowOf(events))
        }

        suspend fun withClearProcessedEvents(eventId: String, id: Long = 1L) = apply {
            coEvery { eventDAO.getEventById(eq(eventId)) }.returns(
                EventEntity(
                    id = id,
                    eventId = eventId,
                    isProcessed = false,
                    payload = ""
                )
            )

            coEvery { eventDAO.deleteProcessedEventsBefore(id) }.returns(Unit)
            coEvery { eventDAO.deleteAllProcessedEvents() }.returns(Unit)
        }

        inline fun arrange(): Pair<Arrangement, EventRepository> {
            return this to eventRepository
        }
    }
}
