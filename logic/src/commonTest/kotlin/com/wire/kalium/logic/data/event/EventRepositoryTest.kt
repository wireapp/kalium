package com.wire.kalium.logic.data.event

import app.cash.turbine.test
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.notification.EventResponse
import com.wire.kalium.network.api.base.authenticated.notification.NotificationApi
import com.wire.kalium.network.api.base.authenticated.notification.NotificationResponse
import com.wire.kalium.network.api.base.authenticated.notification.conversation.MessageEventData
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.MetadataDAO
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
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
                assertEquals(pendingEvent.id, it.id)
            }
            awaitComplete()
        }
    }

    @Test
    fun givenNoSavedLastProcessedId_whenGettingLastProcessedEventId_thenShouldAskFromAPI() = runTest {
        val pendingEventPayload = EventContentDTO.Conversation.NewMessageDTO(
            TestConversation.NETWORK_ID,
            UserId("value", "domain"),
            "eventTime",
            MessageEventData("text", "senderId", "recipient")
        )
        val pendingEvent = EventResponse("pendingEventId", listOf(pendingEventPayload))

        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId(null)
            .withLastNotificationRemote(NetworkResponse.Success(pendingEvent, mapOf(), 200))
            .arrange()

        val result = eventRepository.lastEventId()
        result.shouldSucceed { assertEquals(pendingEvent.id, it) }

        verify(arrangement.notificationApi)
            .suspendFunction(arrangement.notificationApi::lastNotification)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenASavedLastProcessedId_whenGettingLastEventId_thenShouldReturnIt() = runTest {
        val eventId = "dh817h2e"

        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId(eventId)
            .arrange()

        val result = eventRepository.lastEventId()
        result.shouldSucceed { assertEquals(eventId, it) }
    }

    @Test
    fun givenNoLastProcessedEventIdIsStored_thenLastEventIsFetchedFromRemoteAndStored() = runTest {

        val pendingEventPayload = EventContentDTO.Conversation.NewMessageDTO(
            TestConversation.NETWORK_ID,
            UserId("value", "domain"),
            "eventTime",
            MessageEventData("text", "senderId", "recipient")
        )
        val pendingEvent = EventResponse("pendingEventId", listOf(pendingEventPayload))

        val expected = pendingEvent.id
        val (arrangement, eventRepository) = Arrangement()
            .withLastStoredEventId(null)
            .withLastNotificationRemote(NetworkResponse.Success(pendingEvent, mapOf(), 200))
            .withUpdateLastProcessedEventId()
            .arrange()

        eventRepository.lastEventId().shouldSucceed {
            assertEquals(expected, it)
        }

        verify(arrangement.metaDAO)
            .coroutine { arrangement.metaDAO.insertValue(key = LAST_PROCESSED_EVENT_ID_KEY, value = expected) }
            .wasInvoked(exactly = once)
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

        private val eventRepository: EventRepository = EventDataSource(notificationApi, metaDAO, clientIdProvider)

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
                .suspendFunction(notificationApi::lastNotification)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withUpdateLastProcessedEventId() = apply {
            given(metaDAO)
                .suspendFunction(metaDAO::insertValue)
                .whenInvokedWith(any(), any())
        }

        fun arrange(): Pair<Arrangement, EventRepository> {
            given(clientIdProvider)
                .suspendFunction(clientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(TestClient.CLIENT_ID))
            return this to eventRepository
        }
    }
}
