/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.meeting

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.MockConversation
import com.wire.kalium.logic.data.MockProtocolInfo
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnsureMeetingIsMLSEstablishedUseCaseTest {

    @Test
    fun givenGettingConversationFails_whenInvoking_thenReturnsFalseAndDoesNotJoinMLSConversation() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertFalse(result)
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversation(any(), any(), any(), any())
        }
    }

    @Test
    fun givenProteusConversation_whenInvoking_thenReturnsTrueAndDoesNotJoinMLSConversation() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationReturning(Either.Right(MockConversation.group(id = CONVERSATION_ID)))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertTrue(result)
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversation(any(), any(), any(), any())
        }
    }

    @Test
    fun givenEstablishedMLSConversation_whenInvoking_thenReturnsTrueAndDoesNotJoinMLSConversation() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationReturning(Either.Right(mlsConversation(Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertTrue(result)
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.joinExistingMLSConversation(any(), any(), any(), any())
        }
    }

    @Test
    fun givenPendingMLSConversation_whenInvoking_thenJoinsExistingMLSConversationInTransactionAndReturnsTrue() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationReturning(Either.Right(mlsConversation(Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN)))
            .withJoinExistingMLSConversationReturning(Either.Right(Unit))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertTrue(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.cryptoTransactionProvider.transaction<Unit>("ensureMeetingIsMLSEstablished", any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversation(arrangement.transactionContext, CONVERSATION_ID, any(), any())
        }
    }

    @Test
    fun givenPendingMLSConversationAndJoinFails_whenInvoking_thenReturnsFalse() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationReturning(Either.Right(mlsConversation(Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN)))
            .withJoinExistingMLSConversationReturning(Either.Left(CoreFailure.Unknown(RuntimeException("join failed"))))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertFalse(result)
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.joinExistingMLSConversation(arrangement.transactionContext, CONVERSATION_ID, any(), any())
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>(mode = MockMode.autoUnit)
        val joinExistingMLSConversation = mock<JoinExistingMLSConversationUseCase>(mode = MockMode.autoUnit)

        fun withConversationReturning(result: Either<StorageFailure, Conversation>) = apply {
            everySuspend {
                conversationRepository.getConversationById(CONVERSATION_ID)
            } returns result
        }

        fun withJoinExistingMLSConversationReturning(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                joinExistingMLSConversation(any(), any(), any(), any())
            } returns result
        }

        suspend fun arrange(): Pair<Arrangement, EnsureMeetingIsMLSEstablishedUseCase> {
            withTransactionReturning(Either.Right(Unit))
            return this to EnsureMeetingIsMLSEstablishedUseCaseImpl(
                transactionProvider = cryptoTransactionProvider,
                conversationRepository = conversationRepository,
                joinExistingMLSConversation = joinExistingMLSConversation
            )
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation-id", "domain.example")

        fun mlsConversation(groupState: Conversation.ProtocolInfo.MLSCapable.GroupState): Conversation =
            MockConversation.group(
                id = CONVERSATION_ID,
                protocolInfo = MockProtocolInfo.mls().copy(groupState = groupState)
            )
    }
}
