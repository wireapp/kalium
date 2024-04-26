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

package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

class ObserveConversationMembersUseCaseTest {

    @Mock
    private val conversationRepository = mock(ConversationRepository::class)

    @Mock
    private val userRepository = mock(UserRepository::class)

    private lateinit var observeConversationMembers: ObserveConversationMembersUseCase

    @BeforeTest
    fun setup() {
        observeConversationMembers = ObserveConversationMembersUseCaseImpl(
            conversationRepository,
            userRepository,
        )
    }

    @Test
    fun givenAConversationID_whenObservingMembers_thenConversationRepositoryIsCalledWithCorrectID() = runTest {
        val conversationID = TestConversation.ID

        coEvery {
            userRepository.observeSelfUser()
        }.returns(flowOf(TestUser.SELF))

        coEvery {
            userRepository.getKnownUser(any())
        }.returns(flowOf())

        coEvery {
            conversationRepository.observeConversationMembers(any())
        }.returns(flowOf())

        observeConversationMembers(conversationID)

        coVerify {
            conversationRepository.observeConversationMembers(eq(conversationID))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val firstSelfUser = TestUser.SELF
        val secondSelfUser = firstSelfUser.copy(name = "Updated name")
        val selfUserUpdates = listOf(firstSelfUser, secondSelfUser)
        val members = listOf(
            Member(firstSelfUser.id, Member.Role.Member)
        )

        coEvery {
            userRepository.observeUser(eq(firstSelfUser.id))
        }.returns(selfUserUpdates.asFlow())

        coEvery {
            conversationRepository.observeConversationMembers(any())
        }.returns(flowOf(members))

        observeConversationMembers(conversationID).test {
            assertContentEquals(listOf(MemberDetails(firstSelfUser, Member.Role.Member)), awaitItem())
            assertContentEquals(listOf(MemberDetails(secondSelfUser, Member.Role.Member)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenOtherUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val firstOtherUser = TestUser.OTHER
        val secondOtherUser = firstOtherUser.copy(name = "Updated name")
        val members = listOf(
            Member(firstOtherUser.id, Member.Role.Member)
        )

        coEvery {
            userRepository.observeUser(eq(firstOtherUser.id))
        }.returns(
            flow {
                emit(firstOtherUser)
                emit(secondOtherUser)
            }
        )

        coEvery {
            conversationRepository.observeConversationMembers(any())
        }.returns(flowOf(members))

        observeConversationMembers(conversationID).test {
            assertContentEquals(listOf(MemberDetails(firstOtherUser, Member.Role.Member)), awaitItem())
            assertContentEquals(listOf(MemberDetails(secondOtherUser, Member.Role.Member)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenANewMemberIsAdded_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val otherUser = TestUser.OTHER
        val selfUser = TestUser.SELF
        val membersListChannel = Channel<List<Member>>(Channel.UNLIMITED)

        coEvery {
            userRepository.observeUser(eq(TestUser.SELF.id))
        }.returns(flowOf(selfUser))

        coEvery {
            userRepository.observeUser(eq(otherUser.id))
        }.returns(flowOf(otherUser))

        coEvery {
            conversationRepository.observeConversationMembers(eq(conversationID))
        }.returns(membersListChannel.consumeAsFlow())

        observeConversationMembers(conversationID).test {

            membersListChannel.send(listOf(Member(otherUser.id, Member.Role.Member)))
            assertContentEquals(listOf(MemberDetails(otherUser, Member.Role.Member)), awaitItem())

            membersListChannel.send(listOf(Member(otherUser.id, Member.Role.Admin), Member(selfUser.id, Member.Role.Member)))
            assertContentEquals(
                listOf(MemberDetails(otherUser, Member.Role.Admin), MemberDetails(selfUser, Member.Role.Member)),
                awaitItem()
            )

            membersListChannel.close()
            awaitComplete()
        }
    }
}
