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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MemberJoinEventHandlerTest {

    @Test
    fun givenMemberJoinEventWithoutSelfUser_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val newMembers = listOf(Member(TestUser.OTHER_FEDERATED_USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withPersistMembersSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(event.conversationId))
            .wasNotInvoked()
    }

    @Test
    fun givenMemberJoinEventWithSelfUser_whenHandlingIt_thenShouldFetchConversation() = runTest {
        val newMembers = listOf(Member(TestUser.SELF.id, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withPersistMembersSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversation)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistMembers() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withPersistMembersSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistMembers)
            .with(eq(newMembers), eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptPersistingMembersAnyway() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Member))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .withPersistMembersSucceeding()
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::persistMembers)
            .with(eq(newMembers), eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEvent_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers)

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .withPersistMembersSucceeding()
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(
                matching {
                    it is Message.System && it.content is MessageContent.MemberChange
                }
            )
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberJoinEventWithEmptyId_whenHandlingIt_thenShouldPersistSystemMessage() = runTest {
        val newMembers = listOf(Member(TestUser.USER_ID, Member.Role.Admin))
        val event = TestEvent.memberJoin(members = newMembers).copy(id = "")

        val (arrangement, eventHandler) = Arrangement()
            .withPersistingMessageReturning(Either.Right(Unit))
            .withFetchConversationIfUnknownSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .withPersistMembersSucceeding()
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.persistMessage)
            .suspendFunction(arrangement.persistMessage::invoke)
            .with(
                matching {
                    it is Message.System && it.content is MessageContent.MemberChange && it.id.isNotEmpty()
                }
            )
            .wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val persistMessage = mock(classOf<PersistMessageUseCase>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        private val memberJoinEventHandler: MemberJoinEventHandler = MemberJoinEventHandlerImpl(
            conversationRepository,
            userRepository,
            persistMessage,
            TestUser.SELF.id
        )

        fun withPersistingMessageReturning(result: Either<CoreFailure, Unit>) = apply {
            given(persistMessage)
                .suspendFunction(persistMessage::invoke)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun withFetchConversationIfUnknownSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversation)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withPersistMembersSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::persistMembers)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to memberJoinEventHandler
    }

}
