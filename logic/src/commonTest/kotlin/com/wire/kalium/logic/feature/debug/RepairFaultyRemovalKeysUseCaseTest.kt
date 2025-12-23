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
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.cryptography.ExternalSenderKey
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCaseTest.Arrangement.Companion.FAULTY_KEY
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RepairFaultyRemovalKeysUseCaseTest {

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndDomainIsNotTheSameAsUser_thenRepairNotNeeded() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val result = useCase.invoke(TargetedRepairParam("wire.com", listOf("somekey")))

        coVerify { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }.wasNotInvoked()
        assertEquals(RepairResult.RepairNotNeeded, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndNoConversationsFound_thenNoConversationsToRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", listOf("somekey")))

        coVerify { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }.wasInvoked(exactly = 1)
        assertEquals(RepairResult.NoConversationsToRepair, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFound_thenPerformRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(Unit.right())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        coVerify { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.resetMLSConversationUseCase(any(), any()) }.wasInvoked(exactly = 1)
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(1, result.successfullyRepairedConversations)
        assertEquals(0, result.failedRepairs.size)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFoundAndFail_thenPerformRepairCountingFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(CoreFailure.MissingClientRegistration.left())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        coVerify { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }.wasInvoked(exactly = 1)
        coVerify { arrangement.resetMLSConversationUseCase(any(), any()) }.wasInvoked(exactly = 1)
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(0, result.successfullyRepairedConversations)
        assertEquals(1, result.failedRepairs.size)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository = mock(ConversationRepository::class)

        val resetMLSConversationUseCase = mock(ResetMLSConversationUseCase::class)

        suspend fun withMlsConversations(result: Either<CoreFailure, List<Conversation>>) = apply {
            coEvery { conversationRepository.getMLSConversationsByDomain(any()) } returns result
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun withGetExternalKeyForConversation(key: String) = apply {
            coEvery { mlsContext.getExternalSenders(any()) } returns
                    ExternalSenderKey(key.hexToByteArray())
        }

        suspend fun withResetMLSConversationResult(result: Either<CoreFailure, Unit>) = apply {
            coEvery { resetMLSConversationUseCase.invoke(any(), any()) } returns result
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
