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
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.logout.LogoutReason
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.CurrentClientIdProvider
import com.wire.kalium.logic.feature.auth.LogoutUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.arrangement.UserRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.UserRepositoryArrangementImpl
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangement
import com.wire.kalium.logic.util.arrangement.mls.OneOnOneResolverArrangementImpl
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class UserEventReceiverTest {

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsEqualCurrentClient_SoftLogoutInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange {
            withCurrentClientIdIs(CLIENT_ID1)
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(LogoutReason.REMOVED_CLIENT))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenRemoveClientEvent_whenTheClientIdIsNotEqualCurrentClient_SoftLogoutNotInvoked() = runTest {
        val event = TestEvent.clientRemove(EVENT_ID, CLIENT_ID1)
        val (arrangement, eventReceiver) = arrange {
            withCurrentClientIdIs(CLIENT_ID2)
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenDeleteAccountEvent_SoftLogoutInvoked() = runTest {
        val event = TestEvent.userDelete(userId = SELF_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withLogoutUseCaseSucceed()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.logoutUseCase)
            .suspendFunction(arrangement.logoutUseCase::invoke)
            .with(eq(LogoutReason.DELETED_ACCOUNT))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenUserDeleteEvent_RepoAndPersisMessageAreInvoked() = runTest {
        val event = TestEvent.userDelete(userId = OTHER_USER_ID)
        val (arrangement, eventReceiver) = arrange {
            withRemoveUserSuccess()
            withDeleteUserFromConversationsSuccess()
            withConversationsByUserId(listOf(TestConversation.CONVERSATION))
        }

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
        val (arrangement, eventReceiver) = arrange {
            withUpdateUserSuccess()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.userRepository)
            .suspendFunction(arrangement.userRepository::updateUserFromEvent)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewClientEvent_NewClientManagerInvoked() = runTest {
        val event = TestEvent.newClient()
        val (arrangement, eventReceiver) = arrange { }

        eventReceiver.onEvent(event)

        verify(arrangement.clientRepository)
            .suspendFunction(arrangement.clientRepository::saveNewClientEvent)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConnectionEvent_thenConnectionIsPersisted() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING)
        val (arrangement, eventReceiver) = arrange {
            withInsertConnectionFromEventSucceeding()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.connectionRepository)
            .suspendFunction(arrangement.connectionRepository::insertConnectionFromEvent)
            .with(any())
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenNewConnectionEventWithStatusPending_thenActiveOneOnOneConversationIsNotResolved() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.PENDING).copy()
        val (arrangement, eventReceiver) = arrange {
            withInsertConnectionFromEventSucceeding()
        }

        eventReceiver.onEvent(event)

        verify(arrangement.oneOnOneResolver)
            .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUser)
            .with(any())
            .wasNotInvoked()
    }

    @Test
    fun givenNewConnectionEventWithStatusAccepted_thenActiveOneOnOneConversationIsResolved() = runTest {
        val event = TestEvent.newConnection(status = ConnectionState.ACCEPTED).copy()
        val (arrangement, eventReceiver) = arrange {
            withInsertConnectionFromEventSucceeding()
            withResolveOneOnOneConversationWithUserIdReturning(Either.Right(TestConversation.ID))
        }

        eventReceiver.onEvent(event)

        verify(arrangement.oneOnOneResolver)
            .suspendFunction(arrangement.oneOnOneResolver::resolveOneOnOneConversationWithUserId)
            .with(eq(event.connection.qualifiedToId))
            .wasInvoked(exactly = once)
    }

    private class Arrangement(private val block: Arrangement.() -> Unit) :
        UserRepositoryArrangement by UserRepositoryArrangementImpl(),
        OneOnOneResolverArrangement by OneOnOneResolverArrangementImpl()
    {
        @Mock
        val connectionRepository = mock(classOf<ConnectionRepository>())

        @Mock
        val logoutUseCase = mock(classOf<LogoutUseCase>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val currentClientIdProvider = mock(classOf<CurrentClientIdProvider>())

        @Mock
        val clientRepository = mock(classOf<ClientRepository>())

        private val userEventReceiver: UserEventReceiver = UserEventReceiverImpl(
            clientRepository,
            connectionRepository,
            conversationRepository,
            userRepository,
            logoutUseCase,
            oneOnOneResolver,
            SELF_USER_ID,
            currentClientIdProvider
        )

        init {
            withSaveNewClientSucceeding()
        }

        fun withInsertConnectionFromEventSucceeding() = apply {
            given(connectionRepository)
                .suspendFunction(connectionRepository::insertConnectionFromEvent)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withSaveNewClientSucceeding() = apply {
            given(clientRepository)
                .suspendFunction(clientRepository::saveNewClientEvent)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withCurrentClientIdIs(clientId: ClientId) = apply {
            given(currentClientIdProvider)
                .suspendFunction(currentClientIdProvider::invoke)
                .whenInvoked()
                .thenReturn(Either.Right(clientId))
        }

        fun withLogoutUseCaseSucceed() = apply {
            given(logoutUseCase).suspendFunction(logoutUseCase::invoke).whenInvokedWith(any()).thenReturn(Unit)
        }

        fun withDeleteUserFromConversationsSuccess() = apply {
            given(conversationRepository).suspendFunction(conversationRepository::deleteUserFromConversations)
                .whenInvokedWith(any()).thenReturn(Either.Right(Unit))
        }

        fun withConversationsByUserId(conversationIds: List<Conversation>) = apply {
            given(conversationRepository).suspendFunction(conversationRepository::getConversationsByUserId)
                .whenInvokedWith(any()).thenReturn(Either.Right(conversationIds))
        }

        fun arrange() = run {
            block()
            this@Arrangement to userEventReceiver
        }
    }

    companion object {
        private fun arrange(configuration: Arrangement.() -> Unit) = Arrangement(configuration).arrange()

        const val EVENT_ID = "1234"
        val SELF_USER_ID = UserId("alice", "wonderland")
        val OTHER_USER_ID = UserId("john", "public")
        val CLIENT_ID1 = ClientId("clientId1")
        val CLIENT_ID2 = ClientId("clientId2")

    }
}
