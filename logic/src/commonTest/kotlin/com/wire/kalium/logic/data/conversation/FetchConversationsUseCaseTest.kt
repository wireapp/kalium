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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

@OptIn(ConversationPersistenceApi::class)
class FetchConversationsUseCaseTest {

    @Test
    fun whenOnePageOfResults_shouldPersistAndReturnRight() = runTest {
        val batch = batch(
            hasMore = false,
            lastPagingState = "state1",
            found = listOf(TestConversation.CONVERSATION_RESPONSE)
        )

        val (arrangement, useCase) = arrange {
            withFetchConversations(batch)
            withPersistConversationsSuccess()
        }

        val result = useCase()

        assertTrue(result.isRight())
        coVerify { arrangement.conversationRepository.fetchConversations(null) }.wasInvoked(once)
        coVerify {
            arrangement.persistConversations(
                eq(batch.response.conversationsFound),
                eq(true),
                any()
            )
        }.wasInvoked(once)
    }

    @Test
    fun whenMultiplePages_shouldFetchUntilEnd_andPersistAll() = runTest {
        val batch1 = batch(hasMore = true, lastPagingState = "s1")
        val batch2 = batch(hasMore = false, lastPagingState = null)

        val (arrangement, useCase) = arrange {
            withFetchConversationsSequence(listOf(batch1, batch2))
            withPersistConversationsSuccess()
        }

        val result = useCase()

        assertTrue(result.isRight())
        coVerify { arrangement.conversationRepository.fetchConversations(null) }.wasInvoked(once)
        coVerify { arrangement.conversationRepository.fetchConversations("s1") }.wasInvoked(once)
        coVerify {
            arrangement.persistConversations(any(), eq(true), any())
        }.wasInvoked(2)
    }

    @Test
    fun whenFetchFails_shouldReturnLeftAndStop() = runTest {
        val (arrangement, useCase) = arrange {
            withFetchConversationsFails()
        }

        val result = useCase()

        assertTrue(result is Either.Left)
        coVerify { arrangement.persistConversations(any(), any(), any()) }.wasNotInvoked()
    }

    @Test
    fun whenHasFailedAndNotFoundConversations_shouldCallRepoHelpers() = runTest {
        val failed = listOf(
            TestConversation.NETWORK_ID.copy(value = "c1"),
            TestConversation.NETWORK_ID.copy(value = "c2")
        )
        val notFound = listOf(TestConversation.NETWORK_ID.copy(value = "c3"))
        val found = listOf(TestConversation.CONVERSATION_RESPONSE)
        val batch = batch(
            hasMore = false,
            found = found,
            failed = failed,
            notFound = notFound
        )

        val (arrangement, useCase) = arrange {
            withFetchConversations(batch)
            withPersistConversationsSuccess()
            withPersistIncompleteConversations()
        }

        useCase()

        coVerify {
            arrangement.conversationRepository.persistIncompleteConversations(eq(failed))
        }.wasInvoked(once)
    }

    @Test
    fun whenEmptyResult_shouldStillReturnRight() = runTest {
        val emptyBatch = batch(hasMore = false, found = emptyList())

        val (arrangement, useCase) = arrange {
            withFetchConversations(emptyBatch)
            withPersistConversationsSuccess()
        }

        val result = useCase()

        assertTrue(result.isRight())
        coVerify { arrangement.persistConversations(emptyList(), true, false) }.wasInvoked(once)
    }

    @Test
    fun whenFetchReturnsEmptyListMultipleTimes_shouldEventuallyStop() = runTest {
        val batch1 = batch(hasMore = true, lastPagingState = "s1", found = emptyList())
        val batch2 = batch(hasMore = false, lastPagingState = null, found = listOf(TestConversation.CONVERSATION_RESPONSE))

        val (arrangement, useCase) = arrange {
            withFetchConversationsSequence(listOf(batch1, batch2))
            withPersistConversationsSuccess()
        }

        val result = useCase()

        assertTrue(result.isRight())
        coVerify { arrangement.persistConversations(eq(emptyList()), eq(true), eq(false)) }.wasInvoked(once)
        coVerify { arrangement.persistConversations(eq(batch2.response.conversationsFound), eq(true), eq(false)) }
            .wasInvoked(once)
    }


    private fun batch(
        hasMore: Boolean,
        lastPagingState: String? = null,
        found: List<ConversationResponse> = emptyList(),
        failed: List<NetworkQualifiedId> = emptyList(),
        notFound: List<NetworkQualifiedId> = emptyList()
    ): ConversationBatch =
        ConversationBatch(
            response = ConversationResponseDTO(
                conversationsFound = found,
                conversationsFailed = failed,
                conversationsNotFound = notFound
            ),
            hasMore = hasMore,
            lastPagingState = lastPagingState
        )

    private suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, FetchConversationsUseCase> =
        Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl() {

        val persistConversations = mock(PersistConversationsUseCase::class)

        suspend fun withFetchConversations(result: ConversationBatch) = apply {
            coEvery { conversationRepository.fetchConversations(any()) } returns Either.Right(result)
        }

        suspend fun withFetchConversationsSequence(results: List<ConversationBatch>) = apply {
            results.forEachIndexed { index, result ->
                val param = if (index == 0) null else results[index - 1].lastPagingState
                coEvery { conversationRepository.fetchConversations(eq(param)) } returns Either.Right(result)
            }
        }

        suspend fun withFetchConversationsFails() = apply {
            coEvery { conversationRepository.fetchConversations(any()) } returns Either.Left(CoreFailure.Unknown(null))
        }

        suspend fun withPersistConversationsSuccess() = apply {
            coEvery { persistConversations(any(), eq(true), any()) } returns Either.Right(Unit)
        }

        suspend fun withPersistIncompleteConversations() = apply {
            coEvery { conversationRepository.persistIncompleteConversations(any()) } returns Either.Right(Unit)
        }

        suspend fun withPersistConversationsFails() = apply {
            coEvery {
                persistConversations(any(), eq(true), eq(false))
            } returns Either.Left(CoreFailure.Unknown(null))
        }

        fun arrange(): Pair<Arrangement, FetchConversationsUseCase> {
            runBlocking { block() }
            return this to FetchConversationsUseCaseImpl(
                conversationRepository = conversationRepository,
                persistConversations = persistConversations
            )
        }
    }
}
