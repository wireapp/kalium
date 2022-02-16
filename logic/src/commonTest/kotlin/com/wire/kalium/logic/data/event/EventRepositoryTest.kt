package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.id.IdMapperImpl
import com.wire.kalium.network.api.notification.NotificationApi
import com.wire.kalium.persistence.event.EventInfoStorage
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.configure
import io.mockative.given
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class EventRepositoryTest {

    @Mock
    private val notificationApi: NotificationApi = mock(classOf())

    @Mock
    private val eventInfoStorage: EventInfoStorage = configure(mock(classOf())){
        stubsUnitByDefault = true
    }

    @Mock
    private val clientRepository: ClientRepository = mock(classOf())

    @Mock
    private val eventMapper: EventMapper = EventMapper(IdMapperImpl())

    private lateinit var eventRepository: EventRepository

    @BeforeTest
    fun setup() {
        eventRepository = EventDataSource(notificationApi, eventInfoStorage, clientRepository, eventMapper)
    }

    @Test
    fun givenNoEventWasProcessedBefore_whenGettingEvents_thenGetAllNotificationsIsCalled() = runTest {
        given(eventInfoStorage)
            .getter(eventInfoStorage::lastProcessedId)
            .whenInvoked()
            .then { null }

        given(notificationApi)
            .suspendFunction(notificationApi::getAllNotifications)
            .whenInvokedWith(any(), any())
//            .then {
////                NetworkResponse.Success() TODO: Can't mock Network Response!
//            }

        eventRepository.events()

    }
}
