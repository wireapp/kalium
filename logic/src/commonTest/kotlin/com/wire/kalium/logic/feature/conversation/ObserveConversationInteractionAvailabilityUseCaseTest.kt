package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversationDetails
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

class ObserveConversationInteractionAvailabilityUseCaseTest {

    @Test
    fun givenUserIsAGroupMember_whenInvokingInteractionForConversation_thenInteractionShouldBeEnabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = Arrangement()
            .withGroupConversation(isMember = true)
            .arrange()

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.ENABLED), interactionResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
                .with(eq(conversationId))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenUserIsNoLongerAGroupMember_whenInvokingInteractionForConversation_thenInteractionShouldBeEnabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = Arrangement()
            .withGroupConversation(isMember = false)
            .arrange()

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.NOT_MEMBER), interactionResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
                .with(eq(conversationId))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenGroupDetailsReturnsError_whenInvokingInteractionForConversation_thenInteractionShouldReturnFailure() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = Arrangement()
            .withGroupConversationError()
            .arrange()

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertIs<IsInteractionAvailableResult.Failure>(interactionResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
                .with(eq(conversationId))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenOtherUserIsBlocked_whenInvokingInteractionForConversation_thenInteractionShouldBeDisabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = Arrangement()
            .withBlockedUserConversation()
            .arrange()

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.BLOCKED_USER), interactionResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
                .with(eq(conversationId))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    @Test
    fun givenOtherUserIsDeleted_whenInvokingInteractionForConversation_thenInteractionShouldBeDisabled() = runTest {
        val conversationId = TestConversation.ID

        val (arrangement, observeConversationInteractionAvailability) = Arrangement()
            .withDeletedUserConversation()
            .arrange()

        observeConversationInteractionAvailability(conversationId).test {
            val interactionResult = awaitItem()
            assertEquals(IsInteractionAvailableResult.Success(InteractionAvailability.DELETED_USER), interactionResult)

            verify(arrangement.conversationRepository)
                .suspendFunction(arrangement.conversationRepository::observeConversationDetailsById)
                .with(eq(conversationId))
                .wasInvoked(exactly = once)

            awaitComplete()
        }

    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        val observeConversationInteractionAvailability: ObserveConversationInteractionAvailabilityUseCase =
            ObserveConversationInteractionAvailabilityUseCase(conversationRepository)

        fun withGroupConversation(isMember: Boolean) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Right(TestConversationDetails.CONVERSATION_GROUP.copy(isSelfUserMember = isMember))))
        }

        fun withGroupConversationError() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(any())
                .thenReturn(flowOf(Either.Left(StorageFailure.DataNotFound)))
        }

        fun withBlockedUserConversation() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(any())
                .thenReturn(
                    flowOf(
                        Either.Right(
                            TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                                otherUser = TestUser.OTHER.copy(
                                    connectionStatus = ConnectionState.BLOCKED
                                )
                            )
                        )
                    )
                )
        }

        fun withDeletedUserConversation() = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::observeConversationDetailsById)
                .whenInvokedWith(any())
                .thenReturn(
                    flowOf(
                        Either.Right(
                            TestConversationDetails.CONVERSATION_ONE_ONE.copy(
                                otherUser = TestUser.OTHER.copy(
                                    deleted = true
                                )
                            )
                        )
                    )
                )
        }

        fun arrange() = this to observeConversationInteractionAvailability
    }
}
