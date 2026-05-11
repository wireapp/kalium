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
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.common.functional.Either
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.observeIsUserMember(eq(conversationId), eq(selfUser.id))
            }

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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.observeIsUserMember(eq(conversationId), eq(selfUser.id))
            }

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

            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.conversationRepository.observeIsUserMember(eq(conversationId), eq(selfUser.id))
            }

            awaitComplete()
        }

    }

    private class Arrangement {

        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)

        val observeIsSelfUserMember: ObserveIsSelfUserMemberUseCase =
            ObserveIsSelfUserMemberUseCaseImpl(conversationRepository, TestUser.SELF.id)

        suspend fun withExistingMembership() = apply {
            everySuspend {
                conversationRepository.observeIsUserMember(any(), any())
            } returns flowOf(Either.Right(true))
        }

        suspend fun withNonExistingMembership() = apply {
            everySuspend {
                conversationRepository.observeIsUserMember(any(), any())
            } returns flowOf(Either.Right(false))
        }

        suspend fun withExistingMembershipError() = apply {
            everySuspend {
                conversationRepository.observeIsUserMember(any(), any())
            } returns flowOf(Either.Left(CoreFailure.Unknown(null)))
        }

        fun arrange() = this to observeIsSelfUserMember
    }
}
