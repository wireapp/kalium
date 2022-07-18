package com.wire.kalium.logic.sync

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UserEventReceiverTest {

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsEqualCurrentClient_SoftLogoutInvoked() = runTest {
        val event = TestEvent.clientRemove(CLIENT_ID1)
        val (arrangement, eventReceiver) = Arrangement()
            .withCurrentClientIdIs(CLIENT_ID1)
            .withLogoutUseCaseSucceed()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(false))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_SoftLogoutNotInvoked() = runTest {
        val event = TestEvent.clientRemove(CLIENT_ID1)
        val (arrangement, eventReceiver) = Arrangement()
            .withCurrentClientIdIs(CLIENT_ID2)
            .withLogoutUseCaseSucceed()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenDeleteAccountEvent_SoftLogoutInvoked() = runTest {
        val event = TestEvent.userDelete("")
        val (arrangement, eventReceiver) = Arrangement()
            .withLogoutUseCaseSucceed()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(false))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val logoutUseCase = mock(classOf<LogoutUseCase>())

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            connectionRepository, logoutUseCase, clientRepository
        )

        fun withCurrentClientIdIs(clientId: String) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(Either.Right(ClientId(clientId)))

        }

        fun withLogoutUseCaseSucceed() = apply {
            given(logoutUseCase).suspendFunction(logoutUseCase::invoke).whenInvokedWith(any()).thenReturn(Unit)

        }


        fun arrange() = this to userEventReceiver


    }

    companion object {
        const val CLIENT_ID1 = "clientId1"
        const val CLIENT_ID2 = "clientId2"
    }
}
