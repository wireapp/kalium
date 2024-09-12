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
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventRepositoryTest {

    @Test
    fun givenPendingEvents_whenGettingPendingEvents_thenReturnPendingFirstFollowedByComplete() = runTest {
        val pendingEventPayload = EventContentDTO.Conversation.NewMessageDTO(
            qualifiedConversation = TestConversation.NETWORK_ID,
            qualifiedFrom = UserId("value", "domain"),
            time = Instant.UNIX_FIRST_DATE,
            data = MessageEventData("text", "senderId", "recipient")
        )
        val pendingEvent = EventResponse("pendingEventId", listOf(pendingEventPayload))
        val notificationsPageResponse = NotificationResponse("time", false, listOf(pendingEvent))

        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId("someNotificationId")
            .withNotificationsByBatch(NetworkResponse.Success(notificationsPageResponse, mapOf(), 200))
            .arrange()

        eventRepository.pendingEvents().test {
            awaitItem().shouldSucceed {
                assertEquals(pendingEvent.id, it.event.id)
            }
            awaitComplete()
        }
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

    private companion object {
        const val LAST_PROCESSED_EVENT_ID_KEY = "last_processed_event_id"
    }

    private class Arrangement {
        @Mock
        val notificationApi: NotificationApi = mock(NotificationApi::class)

        @Mock
        val metaDAO = mock(MetadataDAO::class)

        @Mock
        val clientIdProvider = mock(CurrentClientIdProvider::class)

        private val eventRepository: EventRepository = EventDataSource(notificationApi, metaDAO, clientIdProvider, TestUser.SELF.id)

        init {
            runBlocking {
                withCurrentClientIdReturning(TestClient.CLIENT_ID)
            }
        }

        suspend fun withDeleteMetadataSucceeding() = apply {
            coEvery {
                metaDAO.deleteValue(any())
            }.returns(Unit)
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

        suspend fun withLastNotificationRemote(result: NetworkResponse<EventResponse>) = apply {
            coEvery {
                notificationApi.mostRecentNotification(any())
            }.returns(result)
        }

        suspend fun withOldestNotificationReturning(result: NetworkResponse<EventResponse>) = apply {
            coEvery {
                notificationApi.oldestNotification(any())
            }.returns(result)
        }

        suspend fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            coEvery {
                clientIdProvider.invoke()
            }.returns(Either.Right(clientId))
        }

        inline fun arrange(): Pair<Arrangement, EventRepository> {
            return this to eventRepository
        }
    }
}
