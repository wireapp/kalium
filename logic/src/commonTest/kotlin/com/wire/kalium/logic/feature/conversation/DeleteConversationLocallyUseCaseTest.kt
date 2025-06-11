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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.common.functional.Either
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class DeleteConversationLocallyUseCaseTest {

    companion object {
        val SUCCESS = Either.Right(Unit)
        val ERROR = Either.Left(CoreFailure.Unknown(null))
        val CONVERSATION_ID = ConversationId("someValue", "someDomain")
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenAllStepsAreSuccessful_thenSuccessResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(true)
            .withDeleteLocalConversation(SUCCESS)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Right<Unit>>(result)
        coVerify { arrangement.clearConversationContent(any(), eq(true)) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenDeleteConversationIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(true)
            .withDeleteLocalConversation(ERROR)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.clearConversationContent(any(), eq(true)) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasInvoked(exactly = 1)
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenClearContentIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearLocalAsset(false)
            .withDeleteLocalConversation(SUCCESS)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.clearConversationContent(any(), eq(true)) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.deleteConversation(any()) }.wasNotInvoked()
    }

    private class Arrangement {

        val conversationRepository = mock(ConversationRepository::class)
        val clearConversationContent = mock(ClearConversationContentUseCase::class)

        suspend fun withDeleteLocalConversation(result: Either<CoreFailure, Unit>) = apply {
            coEvery { conversationRepository.deleteConversation(any()) }.returns(result)
        }

        suspend fun withClearLocalAsset(isSuccess: Boolean) = apply {
            coEvery { clearConversationContent(any(), any()) }.returns(
                if (isSuccess) ClearConversationContentUseCase.Result.Success
                else ClearConversationContentUseCase.Result.Failure(ERROR.value)
            )
        }

        fun arrange() = this to DeleteConversationLocallyUseCaseImpl(
            conversationRepository = conversationRepository,
            clearConversationContent = clearConversationContent
        )
    }
}
