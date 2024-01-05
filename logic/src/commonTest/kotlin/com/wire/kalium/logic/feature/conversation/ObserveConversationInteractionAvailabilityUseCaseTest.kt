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
