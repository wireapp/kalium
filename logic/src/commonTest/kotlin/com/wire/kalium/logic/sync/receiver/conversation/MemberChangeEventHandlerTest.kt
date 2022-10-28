package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation.Member
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestUser
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

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberChangeEventMutedStatus_whenHandlingIt_thenShouldUpdateConversation() = runTest {
        val event = TestEvent.memberChangeMutedStatus()

        val (arrangement, eventHandler) = Arrangement()
            .withFetchConversationIfUnknownSucceeding()
            .withUpdateMutedStatusSucceeding()
            .withFetchUsersIfUnknownByIdsReturning(Either.Right(Unit))
            .arrange()

        eventHandler.handle(event)

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMutedStatus)
            .with(eq(event.conversationId), any(), any())
            .wasInvoked(exactly = once)
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

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
            .wasInvoked(exactly = once)
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

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
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

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::updateMemberFromEvent)
            .with(eq(updatedMember), eq(event.conversationId))
            .wasNotInvoked()

        verify(arrangement.conversationRepository)
            .suspendFunction(arrangement.conversationRepository::fetchConversationIfUnknown)
            .with(eq(event.conversationId))
            .wasNotInvoked()
    }

    private class Arrangement {

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        private val userRepository = mock(classOf<UserRepository>())

        private val memberChangeEventHandler: MemberChangeEventHandler = MemberChangeEventHandlerImpl(conversationRepository)

        fun withFetchConversationIfUnknownSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchConversationIfUnknownFailing(coreFailure: CoreFailure) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::fetchConversationIfUnknown)
                .whenInvokedWith(any())
                .thenReturn(Either.Left(coreFailure))
        }

        fun withUpdateMemberSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateMemberFromEvent)
                .whenInvokedWith(any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withUpdateMutedStatusSucceeding() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::updateMutedStatus)
                .whenInvokedWith(any(), any(), any())
                .thenReturn(Either.Right(Unit))
        }

        fun withFetchUsersIfUnknownByIdsReturning(result: Either<StorageFailure, Unit>) = apply {
            given(userRepository)
                .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
                .whenInvokedWith(any())
                .thenReturn(result)
        }

        fun arrange() = this to memberChangeEventHandler
    }
}
