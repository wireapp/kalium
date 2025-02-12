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

package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class MLSMessageUnpackerTest {

    @Test
    fun givenConversationWithProteusProtocol_whenUnpacking_thenFailWithNotSupportedByProteus() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()

        val (_, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.PROTEUS_PROTOCOL_INFO)
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(messageEvent)
        result.shouldFail { failure ->
            assertEquals(CoreFailure.NotSupportedByProteus, failure)
        }
    }

    @Test
    fun givenConversationWithMixedProtocol_whenUnpacking_thenSucceed() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (_, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MIXED_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(listOf(DECRYPTED_MESSAGE_BUNDLE.copy(commitDelay = commitDelay))))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(messageEvent)
        result.shouldSucceed()

        assertEquals(listOf(MessageUnpackResult.HandshakeMessage), result.getOrNull())
    }

    @Test
    fun givenConversationWithMLSProtocol_whenUnpacking_thenSucceed() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (_, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(listOf(DECRYPTED_MESSAGE_BUNDLE.copy(commitDelay = commitDelay))))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(messageEvent)
        result.shouldSucceed()

        assertEquals(listOf(MessageUnpackResult.HandshakeMessage), result.getOrNull())
    }

    @Test
    fun givenNewMLSMessageEventWithProposal_whenUnpacking_thenScheduleProposalTimer() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturning(Either.Right(listOf(DECRYPTED_MESSAGE_BUNDLE.copy(commitDelay = commitDelay))))
            .withScheduleCommitSucceeding()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(messageEvent)

        coVerify {
            arrangement.pendingProposalScheduler.scheduleCommit(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
        }.wasInvoked(once)
    }

    @Test
    fun givenNewMLSMessageEvent_whenUnpacking_thenDecryptMessage() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturning(Either.Right(listOf(DECRYPTED_MESSAGE_BUNDLE)))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(messageEvent)

        coVerify {
            arrangement.mlsConversationRepository.decryptMessage(matches { it.contentEquals(messageEvent.content.decodeBase64Bytes()) }, eq(TestConversation.GROUP_ID))
        }.wasInvoked(once)
    }

    private class Arrangement {

        @Mock
        val mlsClient = mock(MLSClient::class)

        @Mock
        val mlsClientProvider = mock(MLSClientProvider::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val mlsConversationRepository = mock(MLSConversationRepository::class)

        @Mock
        val pendingProposalScheduler = mock(PendingProposalScheduler::class)

        @Mock
        val subconversationRepository = mock(SubconversationRepository::class)

        private val mlsMessageUnpacker = MLSMessageUnpackerImpl(
            conversationRepository,
            subconversationRepository,
            mlsConversationRepository,
            pendingProposalScheduler,
            SELF_USER_ID
        )

        suspend fun withMLSClientProviderReturningClient() = apply {
            coEvery {
                mlsClientProvider.getMLSClient(any())
            }.returns(Either.Right(mlsClient))
        }

        suspend fun withDecryptMessageReturning(result: Either<CoreFailure, List<DecryptedMessageBundle>>) = apply {
            coEvery {
                mlsConversationRepository.decryptMessage(any(), any())
            }.returns(result)
        }

        suspend fun withScheduleCommitSucceeding() = apply {
            coEvery {
                pendingProposalScheduler.scheduleCommit(any(), any())
            }.returns(Unit)
        }

        suspend fun withGetConversationProtocolInfoSuccessful(protocolInfo: Conversation.ProtocolInfo) = apply {
            coEvery {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(Either.Right(protocolInfo))
        }

        fun arrange() = this to mlsMessageUnpacker
    }
    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val DECRYPTED_MESSAGE_BUNDLE = DecryptedMessageBundle(
            groupID = TestConversation.GROUP_ID,
            applicationMessage = null,
            commitDelay = null,
            identity = null
        )
    }
}
