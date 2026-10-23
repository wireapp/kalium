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
import com.wire.kalium.logic.data.conversation.ResetMLSConversationResult
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.feature.debug.RepairFaultyRemovalKeysUseCaseTest.Arrangement.Companion.FAULTY_KEY
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
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

class RepairFaultyRemovalKeysUseCaseTest {

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndDomainIsNotTheSameAsUser_thenRepairNotNeeded() = runTest {
        val (arrangement, useCase) = Arrangement().arrange()

        val result = useCase.invoke(TargetedRepairParam("wire.com", listOf("somekey")))

        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }
        assertEquals(RepairResult.RepairNotNeeded, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndNoConversationsFound_thenNoConversationsToRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", listOf("somekey")))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }
        assertEquals(RepairResult.NoConversationsToRepair, result)
    }

    @Test
    fun givenLoadingConversationsFails_whenInvoked_thenReturnError() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(CoreFailure.MissingClientRegistration.left())
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.not) { arrangement.resetMLSConversationUseCase(any(), any()) }
        assertEquals(RepairResult.Error, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFound_thenPerformRepair() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Success)
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.resetMLSConversationUseCase(any(), any()) }
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(1, result.successfullyRepairedConversations)
        assertEquals(0, result.failedRepairs.size)
    }

    @Test
    fun givenConfiguredKeyUsesUppercase_whenInvoked_thenPerformRepair() = runTest {
        val (_, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Success)
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", listOf(FAULTY_KEY.first().uppercase())))

        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(1, result.successfullyRepairedConversations)
    }

    @Test
    fun givenKeyInspectionFailsForOneConversation_whenInvoked_thenRepairOthersAndReturnError() = runTest {
        val failingConversation = TestConversation.MLS_CONVERSATION
        val matchingGroupId = GroupID("matching-group")
        val matchingConversation = TestConversation.MLS_CONVERSATION.copy(
            id = ConversationId("matching-conversation", "domain"),
            protocol = TestConversation.MLS_PROTOCOL_INFO.copy(groupId = matchingGroupId)
        )
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(failingConversation, matchingConversation).right())
            .withGetExternalKeyFailureForConversation(TestConversation.GROUP_ID)
            .withGetExternalKeyForConversation(matchingGroupId, FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Success)
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.resetMLSConversationUseCase(matchingConversation.id, any()) }
        assertEquals(RepairResult.Error, result)
    }

    @Test
    fun givenRepairFaultRemovalKeysUseCase_whenInvokedAndConversationsFoundAndFail_thenPerformRepairCountingFailure() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withMlsConversations(listOf(TestConversation.MLS_CONVERSATION).right())
            .withGetExternalKeyForConversation(FAULTY_KEY.first())
            .withResetMLSConversationResult(ResetMLSConversationResult.Failure(CoreFailure.MissingClientRegistration))
            .arrange()

        val result = useCase.invoke(TargetedRepairParam("domain", FAULTY_KEY))

        verifySuspend(VerifyMode.exactly(1)) { arrangement.conversationRepository.getMLSConversationsByDomain(any()) }
        verifySuspend(VerifyMode.exactly(1)) { arrangement.resetMLSConversationUseCase(any(), any()) }
        assertIs<RepairResult.RepairPerformed>(result)
        assertEquals(0, result.successfullyRepairedConversations)
        assertEquals(1, result.failedRepairs.size)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        val conversationRepository: ConversationRepository = mock()
        val resetMLSConversationUseCase: ResetMLSConversationUseCase = mock()

        suspend fun withMlsConversations(result: Either<CoreFailure, List<Conversation>>) = apply {
            everySuspend { conversationRepository.getMLSConversationsByDomain(any()) } returns result
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun withGetExternalKeyForConversation(key: String) = apply {
            everySuspend { mlsContext.getExternalSenders(any()) } returns
                    ExternalSenderKey(key.hexToByteArray())
        }

        @OptIn(ExperimentalStdlibApi::class)
        suspend fun withGetExternalKeyForConversation(groupId: GroupID, key: String) = apply {
            everySuspend { mlsContext.getExternalSenders(eq(groupId.value)) } returns
                    ExternalSenderKey(key.hexToByteArray())
        }

        suspend fun withGetExternalKeyFailureForConversation(groupId: GroupID) = apply {
            everySuspend { mlsContext.getExternalSenders(eq(groupId.value)) } throws
                    IllegalStateException("Conversation not found")
        }

        suspend fun withResetMLSConversationResult(result: ResetMLSConversationResult) = apply {
            everySuspend { resetMLSConversationUseCase.invoke(any(), any()) } returns result
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
