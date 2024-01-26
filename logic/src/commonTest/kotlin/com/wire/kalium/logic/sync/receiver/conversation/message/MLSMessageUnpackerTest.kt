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
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.e2ei.usecase.CheckRevocationListUseCase
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.ktor.util.decodeBase64Bytes
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.matching
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
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

        verify(arrangement.pendingProposalScheduler)
            .suspendFunction(arrangement.pendingProposalScheduler::scheduleCommit)
            .with(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
            .wasInvoked(once)
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

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(eq(DECRYPTED_MESSAGE_BUNDLE))
            .wasNotInvoked()

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::decryptMessage)
            .with(matching { it.contentEquals(messageEvent.content.decodeBase64Bytes()) }, eq(TestConversation.GROUP_ID))
            .wasInvoked(once)
    }

    @Test
    fun givenNewMLSMessageEventWithCrlNewDistributionPoints_whenUnpacking_thenCheckRevocationList() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val decryptedMessageBundleWithDistributionPoints = DECRYPTED_MESSAGE_BUNDLE.copy(
            crlNewDistributionPoints = listOf("https://crl.wire.com/crl.pem", "https://crl2.wire.com/crl.pem")
        )
        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturning(Either.Right(listOf(decryptedMessageBundleWithDistributionPoints)))
            .withCheckRevocationListReturning()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)

        mlsUnpacker.unpackMlsMessage(messageEvent)

        verify(arrangement.mlsConversationRepository)
            .suspendFunction(arrangement.mlsConversationRepository::decryptMessage)
            .with(matching { it.contentEquals(messageEvent.content.decodeBase64Bytes()) }, eq(TestConversation.GROUP_ID))
            .wasInvoked(once)

        verify(arrangement.checkRevocationList)
            .suspendFunction(arrangement.checkRevocationList::invoke)
            .with(any())
            .wasInvoked(twice)

        verify(arrangement.certificateRevocationListRepository)
            .suspendFunction(arrangement.certificateRevocationListRepository::addOrUpdateCRL)
            .with(any(), any())
            .wasInvoked(twice)
    }

    private class Arrangement {

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val mlsConversationRepository = mock(classOf<MLSConversationRepository>())

        @Mock
        val pendingProposalScheduler = mock(classOf<PendingProposalScheduler>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val checkRevocationList = mock(classOf<CheckRevocationListUseCase>())

        @Mock
        val certificateRevocationListRepository = mock(classOf<CertificateRevocationListRepository>())

        private val mlsMessageUnpacker = MLSMessageUnpackerImpl(
            conversationRepository,
            subconversationRepository,
            mlsConversationRepository,
            pendingProposalScheduler,
            SELF_USER_ID,
            checkRevocationList,
            certificateRevocationListRepository
        )

        fun withMLSClientProviderReturningClient() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withDecryptMessageReturning(result: Either<CoreFailure, List<DecryptedMessageBundle>>) = apply {
            given(mlsConversationRepository)
                .suspendFunction(mlsConversationRepository::decryptMessage)
                .whenInvokedWith(anything(), anything())
                .thenReturn(result)
        }
        fun withCheckRevocationListReturning() = apply {
            given(checkRevocationList)
                .suspendFunction(checkRevocationList::invoke)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(ULong.MIN_VALUE))
        }

        fun withScheduleCommitSucceeding() = apply {
            given(pendingProposalScheduler)
                .suspendFunction(pendingProposalScheduler::scheduleCommit)
                .whenInvokedWith(any(), any())
                .thenReturn(Unit)
        }

        fun withGetConversationProtocolInfoSuccessful(protocolInfo: Conversation.ProtocolInfo) = apply {
            given(conversationRepository)
                .suspendFunction(conversationRepository::getConversationProtocolInfo)
                .whenInvokedWith(anything())
                .thenReturn(Either.Right(protocolInfo))
        }

        fun arrange() = this to mlsMessageUnpacker
    }
    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val DECRYPTED_MESSAGE_BUNDLE = DecryptedMessageBundle(
            groupID = TestConversation.GROUP_ID,
            applicationMessage = null,
            commitDelay = null,
            identity = null,
            crlNewDistributionPoints = null
        )
    }
}
