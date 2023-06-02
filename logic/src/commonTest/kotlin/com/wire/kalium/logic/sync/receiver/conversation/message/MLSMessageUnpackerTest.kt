/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.test_util.TestKaliumDispatcher
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.util.DateTimeUtil
import io.mockative.Mock
import io.mockative.any
import io.mockative.anything
import io.mockative.classOf
import io.mockative.eq
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
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

        val (_, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MIXED_PROTOCOL_INFO)
            .withDecryptMessageReturningProposal()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(messageEvent)
        result.shouldSucceed()

        assertEquals(MessageUnpackResult.HandshakeMessage, result.getOrNull())
    }

    @Test
    fun givenConversationWithMLSProtocol_whenUnpacking_thenSucceed() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()

        val (_, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
            .withDecryptMessageReturningProposal()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(messageEvent)
        result.shouldSucceed()

        assertEquals(MessageUnpackResult.HandshakeMessage, result.getOrNull())
    }

    @Test
    fun givenNewMLSMessageEventWithProposal_whenUnpacking_thenScheduleProposalTimer() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturningProposal(commitDelay = commitDelay)
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
    fun givenNewMLSMessageEventWithCommit_whenUnpacking_thenEmitEpochChange() = runTest(TestKaliumDispatcher.default) {
        val eventTimestamp = DateTimeUtil.currentInstant()

        val (arrangement, mlsUnpacker) = Arrangement()
            .withMLSClientProviderReturningClient()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturningProposal(hasEpochChanged = true)
            .arrange()

        val epochChange = async(TestKaliumDispatcher.default) {
            arrangement.epochsFlow.first()
        }
        yield()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(messageEvent)

        assertEquals(TestConversation.GROUP_ID, epochChange.await())
    }

    private class Arrangement {

        @Mock
        val mlsClient = mock(classOf<MLSClient>())

        @Mock
        val mlsClientProvider = mock(classOf<MLSClientProvider>())

        @Mock
        val conversationRepository = mock(classOf<ConversationRepository>())

        @Mock
        val pendingProposalScheduler = mock(classOf<PendingProposalScheduler>())

        @Mock
        val subconversationRepository = mock(classOf<SubconversationRepository>())

        @Mock
        val protoContentMapper = mock(classOf<ProtoContentMapper>())

        val epochsFlow = MutableSharedFlow<GroupID>()

        private val mlsMessageUnpacker = MLSMessageUnpackerImpl(
            mlsClientProvider,
            conversationRepository,
            subconversationRepository,
            pendingProposalScheduler,
            epochsFlow,
            SELF_USER_ID,
            protoContentMapper
        )

        fun withMLSClientProviderReturningClient() = apply {
            given(mlsClientProvider)
                .suspendFunction(mlsClientProvider::getMLSClient)
                .whenInvokedWith(anything())
                .then { Either.Right(mlsClient) }
        }

        fun withDecryptMessageReturningProposal(commitDelay: Long? = null, hasEpochChanged: Boolean = false) = apply {
            given(mlsClient)
                .function(mlsClient::decryptMessage)
                .whenInvokedWith(anything<String>(), anything<ByteArray>())
                .thenReturn(DecryptedMessageBundle(null, commitDelay, null, hasEpochChanged))
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
    }
}
