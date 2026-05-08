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

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation.Protocol
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.framework.TestConversation.CONVERSATION_RESPONSE
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import com.wire.kalium.util.ConversationPersistenceApi
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

@OptIn(ConversationPersistenceApi::class)
internal class UpdateConversationProtocolUseCaseTest {

    @Test
    fun whenLocalOnlyTrue_callsUpdateProtocolLocally_andReturnsTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolLocallySuccess()
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.MLS, localOnly = true)

        // Then
        assertEquals(Either.Right(true), result)
    }

    @Test
    fun whenRemoteUpdateReturnsHasUpdatedTrue_returnsRightTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolRemotelySuccess(hasUpdated = true)
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.PROTEUS, localOnly = false)

        // Then
        assertEquals(Either.Right(true), result)
    }

    @Test
    fun whenRemoteUpdateReturnsHasUpdatedFalse_persistsConversation_andReturnsTrue() = runTest {
        // Given
        val (arrangement, useCase) = Arrangement()
            .withUpdateProtocolRemotelySuccess(hasUpdated = false)
            .withPersistConversationsSuccess()
            .arrange()

        // When
        val result = useCase(arrangement.transactionContext, CONVERSATION_RESPONSE.id.toModel(), Protocol.MLS, false)

        // Then
        assertEquals(Either.Right(true), result)
    }

    class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {
        private val conversationRepository: ConversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        private val persistConversations: PersistConversationsUseCase = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)

        suspend fun withUpdateProtocolLocallySuccess() = apply {
            everySuspend {
                conversationRepository.updateProtocolLocally(any(), any())
            }.returns(
                Either.Right(
                    ConversationProtocolUpdateStatus(
                        response = CONVERSATION_RESPONSE,
                        hasUpdated = true
                    )
                )
            )
        }

        suspend fun withUpdateProtocolRemotelySuccess(hasUpdated: Boolean) = apply {
            everySuspend {
                conversationRepository.updateProtocolRemotely(any(), any())
            }.returns(
                Either.Right(
                    ConversationProtocolUpdateStatus(
                        response = CONVERSATION_RESPONSE,
                        hasUpdated = hasUpdated
                    )
                )
            )
        }

        suspend fun withPersistConversationsSuccess() = apply {
            everySuspend {
                persistConversations(any(), eq(listOf(CONVERSATION_RESPONSE)),  eq(true), any())
            } returns Either.Right(Unit)
        }

        fun arrange(): Pair<Arrangement, UpdateConversationProtocolUseCase> =
            this to UpdateConversationProtocolUseCaseImpl(conversationRepository, persistConversations)
    }
}
