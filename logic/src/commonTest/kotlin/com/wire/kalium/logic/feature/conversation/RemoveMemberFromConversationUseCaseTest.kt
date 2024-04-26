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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class RemoveMemberFromConversationUseCaseTest {

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberIsSuccessful_ThenReturnSuccess() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Right(Unit))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)

        assertIs<RemoveMemberFromConversationUseCase.Result.Success>(result)

        coVerify {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenMemberAndConversation_WhenRemoveMemberFailed_ThenReturnFailure() = runTest {
        val (arrangement, removeMemberUseCase) = Arrangement()
            .withRemoveMemberGroupIs(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = removeMemberUseCase(TestConversation.ID, TestConversation.USER_1)
        assertIs<RemoveMemberFromConversationUseCase.Result.Failure>(result)

        coVerify {
            arrangement.conversationGroupRepository.deleteMember(eq(TestConversation.USER_1), eq(TestConversation.ID))
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val conversationGroupRepository = mock(ConversationGroupRepository::class)

        private val removeMemberUseCase = RemoveMemberFromConversationUseCaseImpl(
            conversationGroupRepository
        )

        suspend fun withRemoveMemberGroupIs(either: Either<CoreFailure, Unit>) = apply {
            coEvery {
                conversationGroupRepository.deleteMember(any(), any())
            }.returns(either)
        }

        fun arrange() = this to removeMemberUseCase
    }
}
