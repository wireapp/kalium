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
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.mlsmigration.MLSMigrator
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MigrateConversationToMLSUseCaseTest {

    @Test
    fun givenMLSConversation_whenMigratingToMLS_thenReturnSuccessWithoutTransaction() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationProtocolInfo(TestConversation.MLS_PROTOCOL_INFO)
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<MigrateConversationToMLSUseCase.Result.Success>(result)
        verifySuspend(VerifyMode.not) {
            arrangement.cryptoTransactionProvider.transaction<Unit>(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsMigrator.migrate(any(), any())
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsMigrator.finalise(any(), any())
        }
    }

    @Test
    fun givenMixedConversation_whenMigratingToMLS_thenFinaliseConversation() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationProtocolInfo(TestConversation.MIXED_PROTOCOL_INFO)
            .withFinaliseResult(Either.Right(Unit))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<MigrateConversationToMLSUseCase.Result.Success>(result)
        verifySuspend {
            arrangement.mlsMigrator.finalise(eq(arrangement.transactionContext), eq(CONVERSATION_ID))
        }
        verifySuspend(VerifyMode.not) {
            arrangement.mlsMigrator.migrate(any(), any())
        }
    }

    @Test
    fun givenProteusConversation_whenMigratingToMLS_thenMigrateAndFinaliseConversation() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationProtocolInfo(Conversation.ProtocolInfo.Proteus)
            .withMigrateResult(Either.Right(Unit))
            .withFinaliseResult(Either.Right(Unit))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<MigrateConversationToMLSUseCase.Result.Success>(result)
        verifySuspend {
            arrangement.mlsMigrator.migrate(eq(arrangement.transactionContext), eq(CONVERSATION_ID))
        }
        verifySuspend {
            arrangement.mlsMigrator.finalise(eq(arrangement.transactionContext), eq(CONVERSATION_ID))
        }
    }

    @Test
    fun givenProteusConversationAndMigrationFails_whenMigratingToMLS_thenReturnFailureAndSkipFinalise() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withConversationProtocolInfo(Conversation.ProtocolInfo.Proteus)
            .withMigrateResult(Either.Left(FAILURE))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<MigrateConversationToMLSUseCase.Result.Failure>(result)
        assertEquals(FAILURE, result.cause)
        verifySuspend(VerifyMode.not) {
            arrangement.mlsMigrator.finalise(any(), any())
        }
    }

    @Test
    fun givenConversationProtocolInfoFails_whenMigratingToMLS_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationProtocolInfoFailure(FAILURE)
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<MigrateConversationToMLSUseCase.Result.Failure>(result)
        assertEquals(FAILURE, result.cause)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val mlsMigrator = mock<MLSMigrator>()

        suspend fun withConversationProtocolInfo(protocolInfo: Conversation.ProtocolInfo) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(eq(CONVERSATION_ID))
            } returns Either.Right(protocolInfo)
        }

        suspend fun withConversationProtocolInfoFailure(failure: StorageFailure) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(eq(CONVERSATION_ID))
            } returns Either.Left(failure)
        }

        suspend fun withMigrateResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                mlsMigrator.migrate(eq(transactionContext), eq(CONVERSATION_ID))
            } returns result
        }

        suspend fun withFinaliseResult(result: Either<CoreFailure, Unit>) = apply {
            everySuspend {
                mlsMigrator.finalise(eq(transactionContext), eq(CONVERSATION_ID))
            } returns result
        }

        suspend fun arrange(): Pair<Arrangement, MigrateConversationToMLSUseCase> {
            withTransactionReturning(Either.Right(Unit))
            return this to MigrateConversationToMLSUseCaseImpl(
                mlsMigrator = mlsMigrator,
                conversationRepository = conversationRepository,
                coreCryptoTransactionProvider = cryptoTransactionProvider
            )
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation-id", "domain.example")
        val FAILURE = StorageFailure.DataNotFound
    }
}
