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

import com.wire.kalium.common.error.CommonizedMLSException
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationWithOtherUserName
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
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(0, result.stats.mixedDriftCount)
        assertEquals(0, result.stats.mlsLeftCount)
        assertEquals(0, result.stats.mixedLeftCount)
        assertEquals(0, result.stats.ccLookupFailedCount)
        assertEquals(1, result.stats.conversationDetails.size)
        assertEquals(ConversationCryptoProtocolType.PROTEUS, result.stats.conversationDetails.first().protocolType)
        assertNull(result.stats.conversationDetails.first().groupId)
        assertNull(result.stats.conversationDetails.first().dbGroupState)
        assertNull(result.stats.conversationDetails.first().dbEpoch)
        assertNull(result.stats.conversationDetails.first().ccEpoch)
        assertEquals(false, result.stats.conversationDetails.first().ccLookupFailed)
        assertEquals(true, result.stats.conversationDetails.first().selfIsMember)
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
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(1, result.stats.conversationDetails.size)
        assertEquals(ConversationCryptoProtocolType.MLS, result.stats.conversationDetails.first().protocolType)
        assertEquals(TestConversation.GROUP_ID.value, result.stats.conversationDetails.first().groupId)
        assertEquals(DetailGroupState.PENDING_JOIN, result.stats.conversationDetails.first().dbGroupState)
        assertEquals(0UL, result.stats.conversationDetails.first().dbEpoch)
        assertEquals(CC_EPOCH, result.stats.conversationDetails.first().ccEpoch)
        assertEquals(false, result.stats.conversationDetails.first().ccLookupFailed)
        assertEquals(true, result.stats.conversationDetails.first().selfIsMember)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsContext.conversationEpoch(eq(TestConversation.GROUP_ID.value))
        }
    }

    @Test
    fun givenEstablishedMLSConversationMissingInCrypto_whenInvoked_thenReturnDriftCount() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION.copy(
            protocol = TestConversation.MLS_CONVERSATION.protocol.establishedCopy()
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mlsConv))
            .withCCEpochConversationNotFound(TestConversation.GROUP_ID.value)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(1, result.stats.mlsDriftCount)
        assertEquals(0, result.stats.ccLookupFailedCount)
        assertEquals(false, result.stats.conversationDetails.first().ccLookupFailed)
        assertNull(result.stats.conversationDetails.first().ccEpoch)
    }

    @Test
    fun givenEstablishedMLSConversationLookupFails_whenInvoked_thenReturnLookupFailureCount() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION.copy(
            protocol = TestConversation.MLS_CONVERSATION.protocol.establishedCopy()
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mlsConv))
            .withCCEpochThrowing(TestConversation.GROUP_ID.value)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(1, result.stats.ccLookupFailedCount)
        assertEquals(true, result.stats.conversationDetails.first().ccLookupFailed)
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
        assertEquals(0, result.stats.mixedDriftCount)
        assertEquals(ConversationCryptoProtocolType.MIXED, result.stats.conversationDetails.first().protocolType)
        assertEquals(CC_EPOCH, result.stats.conversationDetails.first().ccEpoch)
        assertEquals(false, result.stats.conversationDetails.first().ccLookupFailed)
    }

    @Test
    fun givenMixedConversationLookupFails_whenInvoked_thenReturnLookupFailureCount() = runTest {
        val mixedConv = TestConversation.MIXED_CONVERSATION.copy(
            protocol = TestConversation.MIXED_CONVERSATION.protocol.establishedCopy()
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mixedConv))
            .withCCEpochThrowing(TestConversation.GROUP_ID.value)
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.mixedDriftCount)
        assertEquals(1, result.stats.ccLookupFailedCount)
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
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(0, result.stats.mixedDriftCount)
        assertEquals(2, result.stats.ccLookupFailedCount)
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
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(0, result.stats.mixedDriftCount)
    }

    @Test
    fun givenNoConversations_whenInvoked_thenReturnEmptyStats() = runTest {
        val (_, useCase) = Arrangement()
            .withConversations(emptyList<Conversation>())
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.totalConversations)
        assertTrue(result.stats.conversationDetails.isEmpty())
    }

    @Test
    fun givenEstablishedMLSConversationWhereSelfIsNotMember_whenInvoked_thenReturnLeftCountNotDrift() = runTest {
        val mlsConv = TestConversation.MLS_CONVERSATION.copy(
            protocol = TestConversation.MLS_CONVERSATION.protocol.establishedCopy()
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(mlsConv), selfIsMember = false)
            .withAnyCCEpochConversationNotFound()
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(0, result.stats.mlsDriftCount)
        assertEquals(1, result.stats.mlsLeftCount)
        assertEquals(false, result.stats.conversationDetails.first().selfIsMember)
    }

    @Test
    fun givenOneOnOneConversationWithNullNameAndOtherUserName_whenInvoked_thenUseOtherUserName() = runTest {
        val oneOnOneConv = TestConversation.CONVERSATION.copy(name = null)
        val (_, useCase) = Arrangement()
            .withConversations(
                listOf(
                    ConversationWithOtherUserName(
                        conversation = oneOnOneConv,
                        otherUserName = OTHER_USER_NAME,
                        selfIsMember = true,
                    )
                )
            )
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals(OTHER_USER_NAME, result.stats.conversationDetails.first().conversationName)
    }

    @Test
    fun givenSelfConversationWithNullName_whenInvoked_thenUseSelfName() = runTest {
        val selfConv = TestConversation.CONVERSATION.copy(
            name = null,
            type = Conversation.Type.Self,
        )
        val (_, useCase) = Arrangement()
            .withConversations(listOf(selfConv))
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertEquals("self", result.stats.conversationDetails.first().conversationName)
    }

    @Test
    fun givenConversationWithNoNameAndNoOtherUserName_whenInvoked_thenNameIsNull() = runTest {
        val conv = TestConversation.CONVERSATION.copy(name = null)
        val (_, useCase) = Arrangement()
            .withConversations(listOf(conv))
            .arrange()

        val result = useCase()

        assertIs<GetConversationCryptoStatsResult.Success>(result)
        assertNull(result.stats.conversationDetails.first().conversationName)
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

        fun withConversations(conversations: List<Conversation>, selfIsMember: Boolean = true) = apply {
            withConversations(
                conversations.map { conversation ->
                    ConversationWithOtherUserName(
                        conversation = conversation,
                        otherUserName = null,
                        selfIsMember = selfIsMember,
                    )
                }
            )
        }

        fun withConversations(conversations: List<ConversationWithOtherUserName>) = apply {
            everySuspend { conversationRepository.getConversationListWithOtherUserName() } returns Either.Right(
                flowOf(conversations)
            )
        }

        fun withConversationListFailure(failure: StorageFailure) = apply {
            everySuspend { conversationRepository.getConversationListWithOtherUserName() } returns Either.Left(failure)
        }

        fun withCCEpoch(groupId: String, epoch: ULong) = apply {
            everySuspend { mlsContext.conversationEpoch(eq(groupId)) } returns epoch
        }

        fun withCCEpochThrowing(groupId: String) = apply {
            everySuspend { mlsContext.conversationEpoch(eq(groupId)) } throws IllegalStateException("Conversation not found")
        }

        fun withCCEpochConversationNotFound(groupId: String) = apply {
            everySuspend { mlsContext.conversationEpoch(eq(groupId)) } throws CommonizedMLSException(
                failure = MLSFailure.ConversationNotFound,
                cause = IllegalStateException("Conversation not found"),
            )
        }

        fun withAnyCCEpoch(epoch: ULong) = apply {
            everySuspend { mlsContext.conversationEpoch(any()) } returns epoch
        }

        fun withAnyCCEpochThrowing() = apply {
            everySuspend { mlsContext.conversationEpoch(any()) } throws IllegalStateException("Conversation not found")
        }

        fun withAnyCCEpochConversationNotFound() = apply {
            everySuspend { mlsContext.conversationEpoch(any()) } throws CommonizedMLSException(
                failure = MLSFailure.ConversationNotFound,
                cause = IllegalStateException("Conversation not found"),
            )
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
        const val OTHER_USER_NAME = "Other User"
    }
}

private fun Conversation.ProtocolInfo.establishedCopy(): Conversation.ProtocolInfo = when (this) {
    is Conversation.ProtocolInfo.MLS -> copy(groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)
    is Conversation.ProtocolInfo.Mixed -> copy(groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)
    Conversation.ProtocolInfo.Proteus -> this
}
