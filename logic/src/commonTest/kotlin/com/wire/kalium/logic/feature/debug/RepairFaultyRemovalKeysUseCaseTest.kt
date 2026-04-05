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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCaseTest.Arrangement.Companion.FAULTY_KEY
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.matcher.any as mokkeryAny
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepairFaultyRemovalKeysUseCaseTest {

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndDomainIsNotTheSameAsUser_thenRepairNotNeeded() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val result = useCase.invoke(TargetedRepairParam("wire.com", listOf("somekey")))

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.conversationRepository.getMLSConversationsByDomain(mokkeryAny())
        }
        assertEquals(RepairResult.RepairNotNeeded, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndNoConversationsFound_thenNoConversationsToRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", listOf("somekey")))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.getMLSConversationsByDomain(mokkeryAny())
        }
        assertEquals(RepairResult.NoConversationsToRepair, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFound_thenPerformRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Success)
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.getMLSConversationsByDomain(mokkeryAny())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.resetMLSConversationUseCase(mokkeryAny(), mokkeryAny())
        }
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(1, result.successfullyRepairedConversations)
        assertEquals(0, result.failedRepairs.size)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFoundAndFail_thenPerformRepairCountingFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Failure(CoreFailure.MissingClientRegistration))
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.conversationRepository.getMLSConversationsByDomain(mokkeryAny())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.resetMLSConversationUseCase(mokkeryAny(), mokkeryAny())
        }
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(0, result.successfullyRepairedConversations)
        assertEquals(1, result.failedRepairs.size)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock<ConversationRepository>()

        val resetMLSConversationUseCase = mock<ResetMLSConversationUseCase>()

        suspend fun withMlsConversations(result: Either<CoreFailure, List<Conversation>>) = apply {
            everySuspend { conversationRepository.getMLSConversationsByDomain(mokkeryAny()) } returns result
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun withGetExternalKeyForConversation(key: String) = apply {
            everySuspend { mlsContext.getExternalSenders(mokkeryAny()) } returns
                ExternalSenderKey(key.hexToByteArray())
        }

        suspend fun withResetMLSConversationResult(result: ResetMLSConversationResult) = apply {
            everySuspend { resetMLSConversationUseCase.invoke(mokkeryAny(), mokkeryAny()) } returns result
        }

        suspend fun arrange(): Pair<Arrangement, RepairFaultyRemovalKeysUseCaseImpl> {
            withTransactionReturning(Unit.right())
            return this to RepairFaultyRemovalKeysUseCaseImpl(
                selfUserId = TestUser.USER_ID,
                conversationRepository = conversationRepository,
                resetMLSConversation = resetMLSConversationUseCase,
                transactionProvider = cryptoTransactionProvider,
            )
        }

        companion object {
            val FAULTY_KEY = listOf("16665373b6bf396f75914a0bed297d44")
        }
    }
}
