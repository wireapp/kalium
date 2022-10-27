package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.AccountInfo
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
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
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(LogoutReason.DELETED_ACCOUNT))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserUpdateEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.updateUser(userId = USER_ID)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateUserSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateUserFromEvent)
            .with(any())
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
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            connectionRepository, logoutUseCase, userRepository, USER_ID, currentClientIdProvider
        )

        fun withCurrentClientIdIs(clientId: ClientId) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withLogoutUseCaseSucceed() = apply {
            given(logoutUseCase).suspendFunction(logoutUseCase::invoke).whenInvokedWith(any()).thenReturn(Unit)
        }

        fun withUpdateUserSuccess() = apply {
            given(userRepository).suspendFunction(userRepository::updateUserFromEvent).whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun arrange() = this to userEventReceiver
    }

    companion object {
        const val EVENT_ID = "1234"
        val USER_ID = UserId("alice", "wonderland")
        val CLIENT_ID1 = ClientId("clientId1")
        val CLIENT_ID2 = ClientId("clientId2")

        fun validAuthSessionWith(userId: UserId): AccountInfo.Valid = AccountInfo.Valid(userId)
    }
}
