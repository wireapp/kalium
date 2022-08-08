package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.configuration.server.ServerConfig
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.auth.AuthSession
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.stubs.newServerConfig
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

class UserEventReceiverTest {

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsEqualCurrentClient_SoftLogoutInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = Arrangement()
            .withCurrentClientIdIs(CLIENT_ID1)
            .withLogoutUseCaseSucceed()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(LogoutReason.REMOVED_CLIENT))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_SoftLogoutNotInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
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
        val event = TestEvent.userDelete(userId = USER_ID)
        val (arrangement, eventReceiver) = Arrangement()
            .withLogoutUseCaseSucceed()
            .withCurrentSessionReturns(USER_ID)
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(LogoutReason.DELETED_ACCOUNT))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val logoutUseCase = mock(classOf<LogoutUseCase>())

        @Mock
        val sessionRepository: SessionRepository = mock(classOf<SessionRepository>())

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            connectionRepository, logoutUseCase, clientRepository, sessionRepository
        )

        fun withCurrentClientIdIs(clientId: ClientId) = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::currentClientId)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))

        }

        fun withLogoutUseCaseSucceed() = apply {
            given(logoutUseCase).suspendFunction(logoutUseCase::invoke).whenInvokedWith(any()).thenReturn(Unit)
        }

        fun withCurrentSessionReturns(userId: UserId) = apply {
            given(sessionRepository).function(sessionRepository::currentSession).whenInvoked().thenReturn(
                Either.Right(
                    validAuthSessionWith(userId)
                )
            )
        }

        fun arrange() = this to userEventReceiver
    }

    companion object {
        private val randomString get() = Random.nextBytes(64).decodeToString()
        private val TEST_SERVER_CONFIG: ServerConfig = newServerConfig(1)

        const val EVENT_ID = "1234"
        val USER_ID = UserId("alice", "wonderland")
        val CLIENT_ID1 = ClientId("clientId1")
        val CLIENT_ID2 = ClientId("clientId2")

        fun validAuthSessionWith(userId: UserId): AuthSession =
            AuthSession(
                AuthSession.Session.Valid(
                    userId,
                    randomString,
                    randomString,
                    randomString
                ),
                TEST_SERVER_CONFIG.links
            )
    }
}
