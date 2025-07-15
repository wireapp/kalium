/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class FetchConversationIfUnknownUseCaseTest {

    @Test
    fun whenConversationDoesNotExist_shouldFetchIt() = runTest {
        val (arrangement, useCase) = arrange {
            withGetConversationLeft()
            withFetchConversationSuccess()
        }

        useCase(TestConversation.ID)

        coVerify { arrangement.fetchConversation(eq(TestConversation.ID)) }.wasInvoked(once)
    }

    @Test
    fun whenConversationExists_shouldNotFetchIt() = runTest {
        val (arrangement, useCase) = arrange {
            withGetConversationRight()
        }

        useCase(TestConversation.ID)

        coVerify { arrangement.fetchConversation(any()) }.wasNotInvoked()
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, FetchConversationIfUnknownUseCase> =
        Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        val fetchConversation = mock(FetchConversationUseCase::class)

        suspend fun withGetConversationLeft() = apply {
            coEvery {
                conversationRepository.getConversationById(eq(TestConversation.ID))
            } returns Either.Left(StorageFailure.DataNotFound)
        }

        suspend fun withGetConversationRight() = apply {
            coEvery {
                conversationRepository.getConversationById(eq(TestConversation.ID))
            } returns Either.Right(TestConversation.CONVERSATION)
        }

        suspend fun withFetchConversationSuccess() = apply {
            coEvery { fetchConversation(eq(TestConversation.ID)) } returns Either.Right(Unit)
        }

        fun arrange(): Pair<Arrangement, FetchConversationIfUnknownUseCase> {
            runBlocking { block() }
            return this to FetchConversationIfUnknownUseCaseImpl(
                conversationRepository = conversationRepository,
                fetchConversation = fetchConversation
            )
        }
    }
}
