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
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

class DeleteLocalConversationUseCaseTest {

    companion object {
        val SUCCESS = Either.Right(Unit)
        val ERROR = Either.Left(CoreFailure.Unknown(null))
        val CONVERSATION_ID = ConversationId("someValue", "someDomain")
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenAllStepsAreSuccessful_thenSuccessResultIsPropagated() = runTest {
        // given
        val (_, useCase) = Arrangement()
            .withClearContent(SUCCESS)
            .withClearLocalAsset(SUCCESS)
            .withDeleteLocalConversation(SUCCESS)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Right<Unit>>(result)
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenAssetClearIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearContent(SUCCESS)
            .withClearLocalAsset(ERROR)
            .withDeleteLocalConversation(SUCCESS)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasNotInvoked()
        coVerify { arrangement.conversationRepository.deleteLocalConversation(any()) }.wasNotInvoked()
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenContentClearIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearContent(ERROR)
            .withClearLocalAsset(SUCCESS)
            .withDeleteLocalConversation(SUCCESS)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.clearLocalConversationAssets(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.deleteLocalConversation(any()) }.wasNotInvoked()
    }

    @Test
    fun givenDeleteLocalConversationInvoked_whenDeleteConversationIsUnsuccessful_thenErrorResultIsPropagated() = runTest {
        // given
        val (arrangement, useCase) = Arrangement()
            .withClearContent(SUCCESS)
            .withClearLocalAsset(SUCCESS)
            .withDeleteLocalConversation(ERROR)
            .arrange()

        // when
        val result = useCase(CONVERSATION_ID)

        // then
        assertIs<Either.Left<Unit>>(result)
        coVerify { arrangement.clearLocalConversationAssets(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.clearContent(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.conversationRepository.deleteLocalConversation(any()) }.wasInvoked(exactly = 1)
    }

    private class Arrangement {
        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val clearLocalConversationAssets = mock(ClearLocalConversationAssetsUseCase::class)

        suspend fun withClearContent(result: Either<CoreFailure, Unit>) = apply {
            coEvery { conversationRepository.clearContent(any()) }.returns(result)
        }

        suspend fun withDeleteLocalConversation(result: Either<CoreFailure, Unit>) = apply {
            coEvery { conversationRepository.deleteLocalConversation(any()) }.returns(result)
        }

        suspend fun withClearLocalAsset(result: Either<CoreFailure, Unit>) = apply {
            coEvery { clearLocalConversationAssets(any()) }.returns(result)
        }

        fun arrange() = this to DeleteLocalConversationUseCaseImpl(
            conversationRepository = conversationRepository,
            clearLocalConversationAssets = clearLocalConversationAssets
        )
    }
}
