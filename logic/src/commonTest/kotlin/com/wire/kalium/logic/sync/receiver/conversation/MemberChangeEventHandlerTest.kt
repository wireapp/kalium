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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class MemberChangeEventHandlerTest {

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldFetchConversationIfUnknown() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.fetchConversationIfUnknown(eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventMutedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val event = TestEvent.memberChangeMutedStatus()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMutedStatusLocally(Either.Right(Unit))
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.updateMutedStatusLocally(eq(event.conversationId), any(), any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventArchivedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val isNewEventArchiving = true
        val event = TestEvent.memberChangeArchivedStatus(isArchiving = isNewEventArchiving)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateArchivedStatusLocally(Either.Right(Unit))
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.updateArchivedStatusLocally(
                eq(event.conversationId),
                matches { it == isNewEventArchiving },
                any()
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEvent_whenHandlingIt_thenShouldUpdateMembers() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMemberSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventAndFetchConversationFails_whenHandlingIt_thenShouldAttemptUpdateMembersAnyway() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChange(member = updatedMember)

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(event)

        coVerify {

            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))

        }
    }

    @Test
    fun givenMemberChangeEventAndNotRolePresent_whenHandlingIt_thenShouldIgnoreTheEvent() = runTest {
        val updatedMember = Member(TestUser.USER_ID, Member.Role.Admin)
        val event = TestEvent.memberChangeIgnored()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownFailing(NetworkFailure.NoNetworkConnection(null))
            .withUpdateMemberSucceeding()
            .arrange()

        eventHandler.handle(event)

        coVerify {
            arrangement.conversationRepository.updateMemberFromEvent(eq(updatedMember), eq(event.conversationId))
        }.wasNotInvoked()

        coVerify {
            arrangement.conversationRepository.fetchConversationIfUnknown(eq(event.conversationId))
        }.wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        private val userRepository = mock(UserRepository::class)

        private val memberChangeEventHandler: MemberChangeEventHandler = MemberChangeEventHandlerImpl(conversationRepository)

        suspend fun withFetchConversationIfUnknownSucceeding() = apply {
            coEvery {
                conversationRepository.fetchConversationIfUnknown(any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            coEvery {
                conversationRepository.fetchConversationIfUnknown(any())
            }.returns(Either.Left(coreFailure))
        }

        suspend fun withUpdateMemberSucceeding() = apply {
            coEvery {
                conversationRepository.updateMemberFromEvent(any(), any())
            }.returns(Either.Right(Unit))
        }

        suspend fun withUpdateMutedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateMutedStatusLocally(any(), any(), any())
            }.returns(result)
        }

        suspend fun withUpdateArchivedStatusLocally(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                conversationRepository.updateArchivedStatusLocally(any(), any(), any())
            }.returns(result)
        }

        suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            coEvery {
                userRepository.fetchUsersIfUnknownByIds(any())
            }.returns(result)
        }

        fun arrange() = this to memberChangeEventHandler
    }
}
