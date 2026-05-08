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
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestConversation.MLS_PROTOCOL_INFO
import com.wire.kalium.logic.framework.TestConversation.NETWORK_ID
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMockativeImpl
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangement
import com.wire.kalium.logic.util.arrangement.repository.ConversationRepositoryArrangementImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.mockative.coEvery
import io.mockative.any as mockativeAny
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest

class PersistConversationUseCaseTest {

    @Test
    fun whenConversationDoesNotExist_shouldPersistAndReturnTrue() = runTest {
        val (arrangement, useCase) = arrange {
            withGetConversationDetails(Either.Left(StorageFailure.DataNotFound))
            withPersistConversationsSuccess()
        }

        val result = useCase(arrangement.transactionContext, TestConversation.CONVERSATION_RESPONSE)

        assertTrue(result.getOrNull() ?: false)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversations(
                any(),
                listOf(TestConversation.CONVERSATION_RESPONSE),
                false,
                ConversationSyncReason.Other,
            )
        }
    }

    @Test
    fun whenConversationExistsAndGroupStateIsPending_shouldPersistAndReturnTrue() = runTest {
        val existing = TestConversation.MLS_CONVERSATION

        val (arrangement, useCase) = arrange {
            withGetConversationDetails(existing.right())
            withPersistConversationsSuccess()
        }

        val result = useCase(arrangement.transactionContext, TestConversation.CONVERSATION_RESPONSE)

        assertTrue(result.getOrNull() ?: false)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.persistConversations(any(), any(), eq(false), eq(ConversationSyncReason.Other))
        }
    }

    @Test
    fun whenConversationExistsAndGroupStateIsEstablished_shouldNotPersistAndReturnFalse() = runTest {
        val protocol = MLS_PROTOCOL_INFO.copy(
            groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
        )
        val existing = TestConversation.MLS_CONVERSATION.copy(id = NETWORK_ID.toModel(), protocol = protocol)

        val (arrangement, useCase) = arrange {
            withGetConversationDetails(existing.right())
        }

        val result = useCase(arrangement.transactionContext, TestConversation.CONVERSATION_RESPONSE)

        assertFalse(result.getOrNull() ?: true)
        verifySuspend(VerifyMode.exactly(0)) { arrangement.persistConversations(any(), any(), any(), any()) }
    }

    @Test
    fun whenConversationExistsAndNotMLS_shouldNotPersistAndReturnFalse() = runTest {
        val existing = TestConversation.CONVERSATION.copy(id = NETWORK_ID.toModel())

        val (arrangement, useCase) = arrange {
            withGetConversationDetails(existing.right())
        }

        val result = useCase(arrangement.transactionContext, TestConversation.CONVERSATION_RESPONSE)

        assertFalse(result.getOrNull() ?: true)
        verifySuspend(VerifyMode.exactly(0)) { arrangement.persistConversations(any(), any(), any(), any()) }
    }

    private suspend fun arrange(block: suspend Arrangement.() -> Unit): Pair<Arrangement, PersistConversationUseCase> =
        Arrangement(block).arrange()

    private class Arrangement(
        private val block: suspend Arrangement.() -> Unit
    ) : ConversationRepositoryArrangement by ConversationRepositoryArrangementImpl(),
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMockativeImpl() {

        val persistConversations = mock<PersistConversationsUseCase>(mode = MockMode.autoUnit)

        suspend fun withGetConversationDetails(result: Either<StorageFailure, Conversation>) = apply {
            coEvery {
                conversationRepository.getConversationDetails(mockativeAny())
            } returns result
        }

        suspend fun withPersistConversationsSuccess() = apply {
            everySuspend {
                persistConversations(any(), listOf(TestConversation.CONVERSATION_RESPONSE), eq(false), eq(ConversationSyncReason.Other))
            } returns Either.Right(Unit)
        }

        fun arrange(): Pair<Arrangement, PersistConversationUseCase> {
            runBlocking { block() }
            return this to PersistConversationUseCaseImpl(
                conversationRepository = conversationRepository,
                persistConversations = persistConversations
            )
        }
    }
}
