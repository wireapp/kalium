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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.PersistConversationsUseCase
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE_DTO
import com.wire.kalium.logic.test_util.testKaliumDispatcher
import com.wire.kalium.util.KaliumDispatcher
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@ExperimentalCoroutinesApi
class RefreshConversationsWithoutMetadataUseCaseTest {

    @Test
    fun givenConversationsWithoutMetadata_whenRefreshing_thenShouldFetchAndPersist() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement(testKaliumDispatcher)
            .withConversationIdsWithoutMetadata(listOf(TestConversation.ID))
            .withFetchConversationListDetailsSuccess()
            .withPersistConversationsSuccess()
            .arrange()

        // When
        useCase()

        // Then
        coVerify { arrangement.conversationRepository.getConversationIdsWithoutMetadata() }.wasInvoked(once)
        coVerify { arrangement.conversationRepository.fetchConversationListDetails(any()) }.wasInvoked(once)
        coVerify {
            arrangement.persistConversations(
                eq(CONVERSATION_RESPONSE_DTO.conversationsFound),
                eq(false),
                any()
            )
        }.wasInvoked(once)
    }

    @Test
    fun givenNoConversationsWithoutMetadata_whenRefreshing_thenShouldNotFetchOrPersist() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement(testKaliumDispatcher)
            .withConversationIdsWithoutMetadata(emptyList())
            .arrange()

        // When
        useCase()

        // Then
        coVerify { arrangement.conversationRepository.fetchConversationListDetails(any()) }.wasNotInvoked()
        coVerify { arrangement.persistConversations(any(), any(), any()) }.wasNotInvoked()
    }

    private class Arrangement(private val dispatcher: KaliumDispatcher) {
        val conversationRepository = mock(ConversationRepository::class)
        val persistConversations = mock(PersistConversationsUseCase::class)

        suspend fun withConversationIdsWithoutMetadata(ids: List<QualifiedID>) = apply {
            coEvery {
                conversationRepository.getConversationIdsWithoutMetadata()
            }.returns(Either.Right(ids))
        }

        suspend fun withFetchConversationListDetailsSuccess() = apply {
            coEvery {
                conversationRepository.fetchConversationListDetails(any())
            }.returns(Either.Right(CONVERSATION_RESPONSE_DTO))
        }

        suspend fun withPersistConversationsSuccess() = apply {
            coEvery {
                persistConversations(any(), any(), any())
            }.returns(Either.Right(Unit))
        }

        fun arrange(): Pair<Arrangement, RefreshConversationsWithoutMetadataUseCase> =
            this to RefreshConversationsWithoutMetadataUseCaseImpl(
                conversationRepository,
                persistConversations,
                dispatcher
            )
    }
}
