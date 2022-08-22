package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ObserveIsSelfUserMemberUseCaseTest {

    @Test
    fun givenAConversationId_whenUserIsMember_thenTheConversationRepositoryShouldReturnProperValue() = runTest {
        val conversationId = TestConversation.ID
        val selfUser = TestUser.SELF

        val (arrangement, observeIsSelfUserMember) = Arrangement()
            .withExistingMembership()
            .arrange()

        observeIsSelfUserMember(conversationId).test {
            val isMemberResult = awaitItem()
            assertEquals(IsSelfUserMemberResult.Success(true), isMemberResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeIsUserMember)
                .with(eq(conversationId), eq(selfUser.id))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenAConversationId_whenUserIsNotAMember_thenTheConversationRepositoryShouldReturnProperValue() = runTest {
        val conversationId = TestConversation.ID
        val selfUser = TestUser.SELF

        val (arrangement, observeIsSelfUserMember) = Arrangement()
            .withNonExistingMembership()
            .arrange()

        observeIsSelfUserMember(conversationId).test {
            val isMemberResult = awaitItem()
            assertEquals(IsSelfUserMemberResult.Success(false), isMemberResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeIsUserMember)
                .with(eq(conversationId), eq(selfUser.id))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenAConversationId_whenIsMemberReturnsError_thenTheConversationRepositoryShouldReturnProperValue() = runTest {
        val conversationId = TestConversation.ID
        val selfUser = TestUser.SELF

        val (arrangement, observeIsSelfUserMember) = Arrangement()
            .withExistingMembershipError()
            .arrange()

        observeIsSelfUserMember(conversationId).test {
            val isMemberResult = awaitItem()
            assertIs<IsSelfUserMemberResult.Failure>(isMemberResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeIsUserMember)
                .with(eq(conversationId), eq(selfUser.id))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        val observeIsSelfUserMember: ObserveIsSelfUserMemberUseCase =
            ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, TestUser.SELF.id)

        fun withExistingMembership() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .whenInvokedWith(any(), any())
                .thenReturn(flowOf(Either.Right(true)))
        }

        fun withNonExistingMembership() = apply {
            given(conversationRepository).suspendFunction(conversationRepository::observeIsUserMember).whenInvokedWith(any(), any())
                .thenReturn(
                    flowOf(Either.Right(false))
                )
        }

        fun withExistingMembershipError() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeIsUserMember)
                .whenInvokedWith(any(), any())
                .thenReturn(flowOf(Either.Left(CoreFailure.Unknown(null))))
        }

        fun arrange() = this to observeIsSelfUserMember
    }
}
