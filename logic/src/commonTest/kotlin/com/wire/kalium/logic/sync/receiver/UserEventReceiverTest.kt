/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.feature.client.NewClientManager
import com.wire.kalium.logic.feature.client.NewClientManagerImpl
import com.wire.kalium.logic.framework.TestConversation
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
            .withDeleteClientSucceeds()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_ClientIsDeleted() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = Arrangement()
            .withCurrentClientIdIs(CLIENT_ID2)
            .withLogoutUseCaseSucceed()
            .withDeleteClientSucceeds()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::deleteClient)
            .with(eq(event.clientId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenDeleteAccountEvent_SoftLogoutInvoked() = runTest {
        val event = TestEvent.userDelete(userId = SELF_USER_ID)
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
    fun givenUserDeleteEvent_RepoAndPersisMessageAreInvoked() = runTest {
        val event = TestEvent.userDelete(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = Arrangement()
            .withUserDeleteSuccess()
            .withConversationIdsByUserId(listOf(TestConversation.ID))
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::removeUser)
            .with(any())
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::deleteUserFromConversations)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserUpdateEvent_RepoIsInvoked() = runTest {
        val event = TestEvent.updateUser(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = Arrangement()
            .withUpdateUserSuccess()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateUserFromEvent)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewClientEvent_NewClientManagerInvoked() = runTest {
        val event = TestEvent.newClient()
        val (arrangement, eventReceiver) = Arrangement()
            .withInsertClientSucceeds()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.newClientManager)
            .suspendFunction(arrangement.newClientManager::scheduleNewClientEvent)
            .with(any(), eq(SELF_USER_ID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewClientEvent_NewClientIsPersisted() = runTest {
        val event = TestEvent.newClient()
        val (arrangement, eventReceiver) = Arrangement()
            .withInsertClientSucceeds()
            .arrange()

        eventReceiver.onEvent(event)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::insertClient)
            .with(eq(event.client))
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val logoutUseCase = mock(classOf<LogoutUseCase>())

        @Mock
        val userRepository = mock(classOf<UserRepository>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val newClientManager = mock(classOf<NewClientManager>())

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            newClientManager,
            connectionRepository,
            conversationRepository,
            clientRepository,
            userRepository,
            logoutUseCase,
            SELF_USER_ID,
            currentClientIdProvider
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

        fun withInsertClientSucceeds() = apply {
            given(clientRepository).suspendFunction(clientRepository::insertClient).whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withDeleteClientSucceeds() = apply {
            given(clientRepository).suspendFunction(clientRepository::deleteClient).whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withUpdateUserSuccess() = apply {
            given(userRepository).suspendFunction(userRepository::updateUserFromEvent).whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withUserDeleteSuccess() = apply {
            given(userRepository).suspendFunction(userRepository::removeUser)
                .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
            given(conversationRepository).suspendFunction(conversationRepository::deleteUserFromConversations)
                .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withConversationIdsByUserId(conversationIds: List<ConversationId>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::getConversationIdsByUserId)
                .whenInvokedWith(any()).thenReturn(Either.Right(conversationIds))
        }

        fun arrange() = this to userEventReceiver
    }

    companion object {
        const val EVENT_ID = "1234"
        val SELF_USER_ID = UserId("alice", "wonderland")
        val OTHER_USER_ID = UserId("john", "public")
        val CLIENT_ID1 = ClientId("clientId1")
        val CLIENT_ID2 = ClientId("clientId2")

    }
}
