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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals

@OptIn(ExperimentalCoroutinesApi::class)
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
            .suspendFunction(userRepository::observeSelfUser)
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
            .function(syncManager::startSyncIfIdle)
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenAConversationID_whenObservingMembers_thenConversationRepositoryIsCalledWithCorrectID() = runTest {
        val conversationID = TestConversation.ID

        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
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
            Member(firstSelfUser.id, Member.Role.Member)
        )

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(firstSelfUser.id))
            .thenReturn(selfUserUpdates.asFlow())

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(members))

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

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(firstOtherUser.id))
            .thenReturn(flow {
                emit(firstOtherUser)
                emit(secondOtherUser)
            })

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(anything())
            .thenReturn(flowOf(members))

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

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(TestUser.SELF.id))
            .thenReturn(flowOf(selfUser))

        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(eq(otherUser.id))
            .thenReturn(flowOf(otherUser))

        given(conversationRepository)
            .suspendFunction(conversationRepository::observeConversationMembers)
            .whenInvokedWith(eq(conversationID))
            .thenReturn(membersListChannel.consumeAsFlow())

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
