package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.logic.data.conversation.MemberDetails
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import io.mockative.Mock
import io.mockative.anything
import io.mockative.configure
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
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

    @Mock
    private val syncManager = configure(mock(SyncManager::class)) {
        stubsUnitByDefault = true
    }

    private lateinit var observeConversationMembers: ObserveConversationMembersUseCase

    @BeforeTest
    fun setup() {
        observeConversationMembers = ObserveConversationMembersUseCase(
            conversationRepository,
            userRepository,
            syncManager
        )
    }

    @Test
    fun givenAConversationID_whenObservingMembers_thenTheSyncManagerIsCalled() = runTest {
        val conversationID = TestConversation.ID

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationMembers(conversationID)

        verify(syncManager)
            .suspendFunction(syncManager::waitForSyncToComplete)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationID_whenObservingMembers_thenConversationRepositoryIsCalledWithCorrectID() = runTest {
        val conversationID = TestConversation.ID

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        observeConversationMembers(conversationID)

        verify(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .with(eq(conversationID))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenSelfUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val firstSelfUser = TestUser.SELF
        val secondSelfUser = firstSelfUser.copy(name = "Updated name")
        val selfUserUpdates = listOf(firstSelfUser, secondSelfUser)
        val members = listOf(
            Member(firstSelfUser.id)
        )

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(selfUserUpdates.asFlow())

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(members))

        observeConversationMembers(conversationID).test {
            assertContentEquals(listOf(MemberDetails.Self(firstSelfUser)), awaitItem())
            assertContentEquals(listOf(MemberDetails.Self(secondSelfUser)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenOtherUserUpdates_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val firstOtherUser = TestUser.OTHER
        val secondOtherUser = firstOtherUser.copy(name = "Updated name")
        val otherUserUpdates = listOf(firstOtherUser, secondOtherUser)
        val members = listOf(
            Member(firstOtherUser.id)
        )

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(TestUser.SELF))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(otherUserUpdates.asFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(members))

        observeConversationMembers(conversationID).test {
            assertContentEquals(listOf(MemberDetails.Other(firstOtherUser)), awaitItem())
            assertContentEquals(listOf(MemberDetails.Other(secondOtherUser)), awaitItem())
            awaitComplete()
        }
    }

    @Test
    fun givenANewMemberIsAdded_whenObservingMembers_thenTheUpdateIsPropagatedInTheFlow() = runTest {
        val conversationID = TestConversation.ID
        val otherUser = TestUser.OTHER
        val selfUser = TestUser.SELF
        val membersListChannel = Channel<List<Member>>(Channel.UNLIMITED)

        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(flowOf(selfUser))

        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(otherUser))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(membersListChannel.consumeAsFlow())

        observeConversationMembers(conversationID).test {
            membersListChannel.send(listOf(Member(otherUser.id)))
            assertContentEquals(listOf(MemberDetails.Other(otherUser)), awaitItem())

            membersListChannel.send(listOf(Member(otherUser.id), Member(selfUser.id)))
            assertContentEquals(listOf(MemberDetails.Other(otherUser), MemberDetails.Self(selfUser)), awaitItem())

            membersListChannel.close()
            awaitComplete()
        }
    }
}
