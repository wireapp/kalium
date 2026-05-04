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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GetConversationCryptoStatsUseCaseTest {

    @Test
    fun givenOnlyProteusConversations_whenInvoked_thenReturnProteusOnlyStats() = runTest {
        val proteusConv = TestConversation.CONVERSATION
        val (_, useCase) = Arrangement()
            .withConversations(listOf(proteusConv))
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.totalConversations)
        assertEquals(1, result.stats.proteusCount)
        assertEquals(0, result.stats.mlsCount)
        assertEquals(0, result.stats.mixedCount)
        assertEquals(0, result.stats.mlsNotEstablishedInCrypto)
        assertEquals(0, result.stats.mixedNotEstablishedInCrypto)
        assertEquals(1, result.stats.conversationDetails.size)
        assertEquals(ConversationCryptoProtocolType.PROTEUS, result.stats.conversationDetails.first().protocolType)
        assertNull(result.stats.conversationDetails.first().groupId)
        assertNull(result.stats.conversationDetails.first().dbGroupState)
        assertNull(result.stats.conversationDetails.first().dbEpoch)
        assertNull(result.stats.conversationDetails.first().ccEpoch)
        assertNull(result.stats.conversationDetails.first().establishedInCrypto)
    }

    @Test
    fun givenMLSConversationEstablishedInCrypto_whenInvoked_thenReturnCorrectStats() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION
        val (arrangement, useCase) = Arrangement()
            .withConversations(listOf(mlsConv))
            .withCCEpoch(TestConversation.GROUP_ID.value, CC_EPOCH)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.totalConversations)
        assertEquals(0, result.stats.proteusCount)
        assertEquals(1, result.stats.mlsCount)
        assertEquals(0, result.stats.mixedCount)
        assertEquals(0, result.stats.mlsNotEstablishedInCrypto)
        assertEquals(1, result.stats.conversationDetails.size)
        assertEquals(ConversationCryptoProtocolType.MLS, result.stats.conversationDetails.first().protocolType)
        assertEquals(TestConversation.GROUP_ID.value, result.stats.conversationDetails.first().groupId)
        assertEquals(DetailGroupState.PENDING_JOIN, result.stats.conversationDetails.first().dbGroupState)
        assertEquals(0UL, result.stats.conversationDetails.first().dbEpoch)
        assertEquals(CC_EPOCH, result.stats.conversationDetails.first().ccEpoch)
        assertEquals(true, result.stats.conversationDetails.first().establishedInCrypto)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.conversationEpoch(eq(TestConversation.GROUP_ID.value))
        }
    }

    @Test
    fun givenMLSConversationNotEstablishedInCrypto_whenInvoked_thenReturnNotEstablishedCount() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mlsConv))
            .withCCEpochThrowing(TestConversation.GROUP_ID.value)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.mlsNotEstablishedInCrypto)
        assertEquals(false, result.stats.conversationDetails.first().establishedInCrypto)
        assertNull(result.stats.conversationDetails.first().ccEpoch)
    }

    @Test
    fun givenMixedConversation_whenInvoked_thenReturnMixedStats() = runTest {
        val mixedConv = TestConversation.MIXED_CONVERSATION
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mixedConv))
            .withCCEpoch(TestConversation.GROUP_ID.value, CC_EPOCH)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.mixedCount)
        assertEquals(0, result.stats.mixedNotEstablishedInCrypto)
        assertEquals(ConversationCryptoProtocolType.MIXED, result.stats.conversationDetails.first().protocolType)
        assertEquals(CC_EPOCH, result.stats.conversationDetails.first().ccEpoch)
        assertEquals(true, result.stats.conversationDetails.first().establishedInCrypto)
    }

    @Test
    fun givenMixedConversationNotEstablishedInCrypto_whenInvoked_thenReturnNotEstablishedCount() = runTest {
        val mixedConv = TestConversation.MIXED_CONVERSATION
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mixedConv))
            .withCCEpochThrowing(TestConversation.GROUP_ID.value)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.mixedNotEstablishedInCrypto)
    }

    @Test
    fun givenMixedProtocols_whenInvoked_thenReturnCorrectAggregatedStats() = runTest {
        val proteusConv = TestConversation.CONVERSATION
        val mlsConv = TestConversation.MLS_CONVERSATION
        val mixedConv = TestConversation.MIXED_CONVERSATION.copy(
            id = ConversationId("mixed_conv_id", "domain")
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(proteusConv, mlsConv, mixedConv))
            .withAnyCCEpochThrowing()
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(3, result.stats.totalConversations)
        assertEquals(1, result.stats.proteusCount)
        assertEquals(1, result.stats.mlsCount)
        assertEquals(1, result.stats.mixedCount)
        assertEquals(1, result.stats.mlsNotEstablishedInCrypto)
        assertEquals(1, result.stats.mixedNotEstablishedInCrypto)
    }

    @Test
    fun givenAllEstablished_whenInvoked_thenReturnZeroNotEstablished() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION
        val mixedConv = TestConversation.MIXED_CONVERSATION.copy(
            id = ConversationId("mixed_conv_id", "domain")
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mlsConv, mixedConv))
            .withAnyCCEpoch(CC_EPOCH)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.mlsNotEstablishedInCrypto)
        assertEquals(0, result.stats.mixedNotEstablishedInCrypto)
    }

    @Test
    fun givenNoConversations_whenInvoked_thenReturnEmptyStats() = runTest {
        val (_, useCase) = Arrangement()
            .withConversations(emptyList())
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.totalConversations)
        assertTrue(result.stats.conversationDetails.isEmpty())
    }

    @Test
    fun givenRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationListFailure(StorageFailure.DataNotFound)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Failure>(result)
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()

        fun withConversations(conversations: List<Conversation>) = apply {
            everySuspend { conversationRepository.getConversationList() } returns Either.Right(
                flowOf(conversations)
            )
        }

        fun withConversationListFailure(failure: StorageFailure) = apply {
            everySuspend { conversationRepository.getConversationList() } returns Either.Left(failure)
        }

        fun withCCEpoch(groupId: String, epoch: ULong) = apply {
            everySuspend { mlsContext.conversationEpoch(eq(groupId)) } returns epoch
        }

        fun withCCEpochThrowing(groupId: String) = apply {
            everySuspend { mlsContext.conversationEpoch(eq(groupId)) } throws IllegalStateException("Conversation not found")
        }

        fun withAnyCCEpoch(epoch: ULong) = apply {
            everySuspend { mlsContext.conversationEpoch(any()) } returns epoch
        }

        fun withAnyCCEpochThrowing() = apply {
            everySuspend { mlsContext.conversationEpoch(any()) } throws IllegalStateException("Conversation not found")
        }

        suspend fun arrange(): Pair<Arrangement, GetConversationCryptoStatsUseCaseImpl> {
            withMLSTransactionReturning(Unit.right())
            return this to GetConversationCryptoStatsUseCaseImpl(
                conversationRepository = conversationRepository,
                transactionProvider = cryptoTransactionProvider,
            )
        }
    }

    private companion object {
        const val CC_EPOCH = 42UL
    }
}
