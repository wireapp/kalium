package com.wire.kalium.logic.data.event

import app.cash.turbine.test
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.logic.framework.TestClient
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.notification.EventContentDTO
import com.wire.kalium.network.api.notification.EventResponse
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.network.api.notification.NotificationResponse
import com.wire.kalium.network.api.notification.conversation.MessageEventData
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.event.EventInfoStorage
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class EventRepositoryTest {

    @Mock
    private val notificationApi: NotificationApi = mock(classOf<NotificationApi>())

    @Mock
    private val eventInfoStorage: EventInfoStorage = configure(mock(classOf<EventInfoStorage>())) {
        stubsUnitByDefault = true
    }

    @Mock
    private val clientRepository: ClientRepository = mock(classOf<ClientRepository>())

    @Mock
    private val eventMapper: EventMapper = EventMapper(IdMapperImpl())

    private lateinit var eventRepository: EventRepository

    @BeforeTest
    fun setup() {
        eventRepository = EventDataSource(notificationApi, eventInfoStorage, clientRepository, eventMapper)
    }

    @Test
    fun givenNoEventWasProcessedBefore_whenGettingEvents_thenGetAllNotificationsIsCalled() = runTest {
        val firstPage = EventResponse(
            "eventId",
            listOf()
        )
        val notificationsPageResponse = NotificationResponse("time", false, listOf(firstPage))

        given(eventInfoStorage)
            .getter(eventInfoStorage::lastProcessedId)
            .whenInvoked()
            .thenReturn(null)

        given(notificationApi)
            .suspendFunction(notificationApi::getAllNotifications)
            .whenInvokedWith(any(), any())
            .thenReturn(NetworkResponse.Success(notificationsPageResponse, mapOf(), 200))

        given(notificationApi)
            .suspendFunction(notificationApi::listenToLiveEvents)
            .whenInvokedWith(any())
            .thenReturn(flowOf())

        val clientId = TestClient.CLIENT_ID
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(clientId))

        eventRepository.events().collect()

        verify(notificationApi)
            .suspendFunction(notificationApi::getAllNotifications)
            .with(any(), eq(clientId.value))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSomeEventWasProcessedBefore_whenGettingEvents_thenGetByBatchStartingOnLastProcessedID() = runTest {
        val lastProcessedEventId = "someNotificationID"
        val firstPage = EventResponse(
            "eventId",
            listOf(
                EventContentDTO.Conversation.NewMessageDTO(
                    TestConversation.NETWORK_ID,
                    UserId("value", "domain"),
                    "eventTime",
                    MessageEventData("text", "senderId", "recipient")
                )
            )
        )
        val notificationsPageResponse = NotificationResponse("time", false, listOf(firstPage))

        given(eventInfoStorage)
            .getter(eventInfoStorage::lastProcessedId)
            .whenInvoked()
            .thenReturn(lastProcessedEventId)

        given(notificationApi)
            .suspendFunction(notificationApi::notificationsByBatch)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(NetworkResponse.Success(notificationsPageResponse, mapOf(), 200))

        given(notificationApi)
            .suspendFunction(notificationApi::listenToLiveEvents)
            .whenInvokedWith(any())
            .thenReturn(flowOf())

        val clientId = TestClient.CLIENT_ID
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(clientId))

        eventRepository.events().collect()

        verify(notificationApi)
            .suspendFunction(notificationApi::notificationsByBatch)
            .with(any(), eq(clientId.value), eq(lastProcessedEventId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenPendingEventsAndLiveEvents_whenGettingEvents_thenReturnPendingFirstFollowedByLive() = runTest {
        val pendingEventPayload = EventContentDTO.Conversation.NewMessageDTO(
            TestConversation.NETWORK_ID,
            UserId("value", "domain"),
            "eventTime",
            MessageEventData("text", "senderId", "recipient")
        )
        val pendingEvent = EventResponse("pendingEventId", listOf(pendingEventPayload))
        val liveEvent = pendingEvent.copy(id = "liveEventId")
        val notificationsPageResponse = NotificationResponse("time", false, listOf(pendingEvent))

        given(eventInfoStorage)
            .getter(eventInfoStorage::lastProcessedId)
            .whenInvoked()
            .thenReturn("someNotificationId")

        given(notificationApi)
            .suspendFunction(notificationApi::notificationsByBatch)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(NetworkResponse.Success(notificationsPageResponse, mapOf(), 200))

        given(notificationApi)
            .suspendFunction(notificationApi::listenToLiveEvents)
            .whenInvokedWith(any())
            .thenReturn(flowOf(liveEvent))

        val clientId = TestClient.CLIENT_ID
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(clientId))

        eventRepository.events().test {
            awaitItem().shouldSucceed {
                assertEquals(pendingEvent.id, it.id)
            }
            awaitItem().shouldSucceed {
                assertEquals(liveEvent.id, it.id)
            }
            awaitComplete()
        }
    }

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

        given(eventInfoStorage)
            .getter(eventInfoStorage::lastProcessedId)
            .whenInvoked()
            .thenReturn("someNotificationId")

        given(notificationApi)
            .suspendFunction(notificationApi::notificationsByBatch)
            .whenInvokedWith(any(), any(), any())
            .thenReturn(NetworkResponse.Success(notificationsPageResponse, mapOf(), 200))

        val clientId = TestClient.CLIENT_ID
        given(clientRepository)
            .function(clientRepository::currentClientId)
            .whenInvoked()
            .thenReturn(Either.Right(clientId))

        eventRepository.pendingEvents().test {
            awaitItem().shouldSucceed {
                assertEquals(pendingEvent.id, it.id)
            }
            awaitComplete()
        }
    }
}
