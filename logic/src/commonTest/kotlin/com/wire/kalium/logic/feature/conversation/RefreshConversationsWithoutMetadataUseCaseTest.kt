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
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.util.KaliumDispatcher
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
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
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.getConversationIdsWithoutMetadata() }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.fetchConversationListDetails(any()) }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversations(
                any(),
                eq(CONVERSATION_RESPONSE_DTO.conversationsFound),
                eq(false),
                any()
            )
        }
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
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.fetchConversationListDetails(any()) }
        verifySuspend(VerifyMode.not) { arrangement.persistConversations(any(), any(), any(), any()) }
    }

    private class Arrangement(private val dispatcher: KaliumDispatcher) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val persistConversations = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)

        suspend fun withConversationIdsWithoutMetadata(ids: List<QualifiedID>) = apply {
            everySuspend {
                conversationRepository.getConversationIdsWithoutMetadata()
            } returns Either.Right(ids)
        }

        suspend fun withFetchConversationListDetailsSuccess() = apply {
            everySuspend {
                conversationRepository.fetchConversationListDetails(any())
            } returns Either.Right(CONVERSATION_RESPONSE_DTO)
        }

        suspend fun withPersistConversationsSuccess() = apply {
            everySuspend {
                persistConversations(any(), any(), any(), any())
            } returns Either.Right(Unit)
        }

        suspend fun arrange(): Pair<Arrangement, RefreshConversationsWithoutMetadataUseCase> =
            this to RefreshConversationsWithoutMetadataUseCaseImpl(
                conversationRepository,
                persistConversations,
                cryptoTransactionProvider,
                dispatcher
            ).also {
                withTransactionReturning(Either.Right(Unit))
            }
    }
}
