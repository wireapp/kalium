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
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class EventRepositoryTest {

    @Test
    fun givenPendingEvents_whenGettingPendingEvents_thenReturnPendingFirstFollowedByComplete() = runTest {
        val pendingEventPayload = EventContentDTO.Conversation.NewMessageDTO(
            TestConversation.NETWORK_ID,
            UserId("value", "domain"),
            "eventTime",
            MessageEventData("text", "senderId", "recipient")
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

        verify(arrangement.notificationApi)
            .suspendFunction(arrangement.notificationApi::oldestNotification)
            .with(eq(currentClientId.value))
            .wasInvoked(exactly = once)
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
        val notificationApi: NotificationApi = mock(classOf<NotificationApi>())

        @Mock
        val metaDAO = configure(mock(classOf<MetadataDAO>())) { stubsUnitByDefault = true }

        @Mock
        val clientIdProvider = mock(CurrentClientIdProvider::class)

        private val eventRepository: EventRepository = EventDataSource(notificationApi, metaDAO, clientIdProvider, TestUser.SELF.id)

        init {
            withCurrentClientIdReturning(TestClient.CLIENT_ID)
        }

        fun withDeleteMetadataSucceeding() = apply {
            given(metaDAO)
                .suspendFunction(metaDAO::deleteValue)
                .whenInvokedWith(any())
                .thenReturn(Unit)
        }

        suspend fun withLastStoredEventId(value: String?) = apply {
            given(metaDAO)
                .coroutine { metaDAO.valueByKey(LAST_PROCESSED_EVENT_ID_KEY) }
                .thenReturn(value)
        }

        fun withNotificationsByBatch(result: NetworkResponse<NotificationResponse>) = apply {
            given(notificationApi)
                .suspendFunction(notificationApi::notificationsByBatch)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(result)
        }

        fun withLastNotificationRemote(result: NetworkResponse<EventResponse>) = apply {
            given(notificationApi)
                .suspendFunction(notificationApi::mostRecentNotification)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withOldestNotificationReturning(result: NetworkResponse<EventResponse>) = apply {
            given(notificationApi)
                .suspendFunction(notificationApi::oldestNotification)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withCurrentClientIdReturning(clientId: ClientId) = apply {
            given(clientIdProvider)
                .suspendFunction(clientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withUpdateLastProcessedEventId() = apply {
            given(metaDAO)
                .suspendFunction(metaDAO::insertValue)
                .whenInvokedWith(any(), any())
        }

        fun arrange(): Pair<Arrangement, EventRepository> {
            return this to eventRepository
        }
    }
}
