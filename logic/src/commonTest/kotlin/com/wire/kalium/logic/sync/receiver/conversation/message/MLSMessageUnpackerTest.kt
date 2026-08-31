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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ApplicationMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementMokkeryImpl
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.util.DateTimeUtil
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

internal class MLSMessageUnpackerTest {

    @Test
    fun givenConversationWithProteusProtocol_whenUnpacking_thenFailWithNotSupportedByProteus() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()

        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.PROTEUS_PROTOCOL_INFO)
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, messageEvent)
        result.shouldFail { failure ->
            assertEquals(CoreFailure.NotSupportedByProteus, failure)
        }
    }

    @Test
    fun givenConversationWithMixedProtocol_whenUnpacking_thenSucceed() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MIXED_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(listOf(decryptedProposal(commitDelay))))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, messageEvent)
        result.shouldSucceed()

        assertEquals(listOf(MessageUnpackResult.HandshakeMessage), result.getOrNull())
    }

    @Test
    fun givenConversationWithMLSProtocol_whenUnpacking_thenSucceed() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(listOf(decryptedProposal(commitDelay))))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        val result = mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, messageEvent)
        result.shouldSucceed()

        assertEquals(listOf(MessageUnpackResult.HandshakeMessage), result.getOrNull())
    }

    @Test
    fun givenNewMLSMessageEventWithProposal_whenUnpacking_thenScheduleProposalTimer() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val commitDelay: Long = 10

        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturning(Either.Right(listOf(decryptedProposal(commitDelay))))
            .withScheduleCommitSucceeding()
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, messageEvent)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.pendingProposalScheduler.scheduleCommit(eq(TestConversation.GROUP_ID), eq(eventTimestamp.plus(commitDelay.seconds)))
        }
    }

    @Test
    fun givenNewMLSMessageEvent_whenUnpacking_thenDecryptMessage() = runTest {
        val eventTimestamp = DateTimeUtil.currentInstant()
        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_CONVERSATION.protocol)
            .withDecryptMessageReturning(Either.Right(listOf(DECRYPTED_COMMIT_BUNDLE)))
            .arrange()

        val messageEvent = TestEvent.newMLSMessageEvent(eventTimestamp)
        mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, messageEvent)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.decryptMessage(
                any(),
                matches { it.contentEquals(Base64.decode(messageEvent.content)) },
                eq(TestConversation.GROUP_ID)
            )
        }
    }

    @Test
    fun givenEmptyDecryptedBundleList_whenUnpacking_thenReturnHandshakeMessage() = runTest {
        val (arrangement, mlsUnpacker) = Arrangement()
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(emptyList()))
            .arrange()

        val result = mlsUnpacker.unpackMlsMessage(
            arrangement.mlsContext,
            TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant()),
        )

        result.shouldSucceed {
            assertEquals(listOf(MessageUnpackResult.HandshakeMessage), it)
        }
    }

    @Test
    fun givenSubconversationGroupExists_whenUnpacking_thenDecryptUsingSubconversationGroup() = runTest {
        val subConversationId = SubconversationId("conference")
        val subConversationGroupId = GroupID("subconversation-group")
        val (arrangement, mlsUnpacker) = Arrangement()
            .withSubconversationInfo(subConversationGroupId)
            .withDecryptMessageReturning(Either.Right(emptyList()))
            .arrange()
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant(), subConversationId)

        mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.decryptMessage(any(), any(), eq(subConversationGroupId))
        }
        verifySuspend(VerifyMode.not) { arrangement.conversationRepository.getConversationProtocolInfo(any()) }
    }

    @Test
    fun givenSubconversationGroupIsMissing_whenUnpacking_thenFallBackToParentConversationGroup() = runTest {
        val subConversationId = SubconversationId("conference")
        val (arrangement, mlsUnpacker) = Arrangement()
            .withSubconversationInfo(null)
            .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
            .withDecryptMessageReturning(Either.Right(emptyList()))
            .arrange()
        val event = TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant(), subConversationId)

        mlsUnpacker.unpackMlsMessage(arrangement.mlsContext, event)

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.mlsConversationRepository.decryptMessage(any(), any(), eq(TestConversation.GROUP_ID))
        }
    }

    @Test
    fun givenBufferedMlsFailure_whenUnpacking_thenReadEpochAndPropagateFailure() = runTest {
        listOf(MLSFailure.BufferedFutureMessage, MLSFailure.BufferedCommit).forEach { failure ->
            val (arrangement, mlsUnpacker) = Arrangement()
                .withGetConversationProtocolInfoSuccessful(TestConversation.MLS_PROTOCOL_INFO)
                .withDecryptMessageReturning(Either.Left(failure))
                .withLocalGroupEpoch(Either.Right(42UL))
                .arrange()

            val result = mlsUnpacker.unpackMlsMessage(
                arrangement.mlsContext,
                TestEvent.newMLSMessageEvent(DateTimeUtil.currentInstant()),
            )

            result.shouldFail { assertEquals(failure, it) }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.mlsConversationRepository.getLocalGroupEpoch(any(), eq(TestConversation.GROUP_ID))
            }
        }
    }

    @Test
    fun givenReadableApplicationBundle_whenUnpacking_thenMapApplicationMessage() = runTest {
        val readable = ProtoContent.Readable(
            messageUid = "message-id",
            messageContent = MessageContent.Text("hello"),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED,
        )
        val applicationMessage = ApplicationMessage(
            message = byteArrayOf(1, 2, 3),
            senderID = SELF_USER_ID,
            senderClientID = ClientId("client-id"),
        )
        val (_, mlsUnpacker) = Arrangement()
            .withDecodedProtoContent(readable)
            .arrange()

        val result = mlsUnpacker.unpackMlsBundle(
            decryptedText(applicationMessage),
            TestConversation.ID,
            DateTimeUtil.currentInstant(),
        )

        assertIs<MessageUnpackResult.ApplicationMessage>(result)
        assertEquals(readable, result.content)
        assertEquals(SELF_USER_ID, result.senderUserId)
        assertEquals(applicationMessage.senderClientID, result.senderClientId)
    }

    @Test
    fun givenExternalApplicationBundle_whenUnpacking_thenRejectNestedExternalContent() = runTest {
        val external = ProtoContent.ExternalMessageInstructions(
            messageUid = "message-id",
            otrKey = byteArrayOf(1),
            sha256 = null,
            encryptionAlgorithm = null,
        )
        val (_, mlsUnpacker) = Arrangement()
            .withDecodedProtoContent(external)
            .arrange()

        assertFailsWith<KaliumSyncException> {
            mlsUnpacker.unpackMlsBundle(
                decryptedText(ApplicationMessage(byteArrayOf(1), SELF_USER_ID, ClientId("client-id"))),
                TestConversation.ID,
                DateTimeUtil.currentInstant(),
            )
        }
    }

    private class Arrangement : CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementMokkeryImpl() {
        val conversationRepository = mock<ConversationRepository>()
        val mlsConversationRepository = mock<MLSConversationRepository>()
        val pendingProposalScheduler = mock<PendingProposalScheduler>(mode = MockMode.autoUnit)
        val subconversationRepository = mock<SubconversationRepository>(mode = MockMode.autoUnit)
        val protoContentMapper = mock<ProtoContentMapper>()

        private val mlsMessageUnpacker = MLSMessageUnpackerImpl(
            conversationRepository,
            subconversationRepository,
            mlsConversationRepository,
            pendingProposalScheduler,
            SELF_USER_ID,
            protoContentMapper,
        )


        suspend fun withDecryptMessageReturning(result: Either<CoreFailure, List<DecryptedMessageBundle>>) = apply {
            everySuspend {
                mlsConversationRepository.decryptMessage(any(), any(), any())
            }.returns(result)
        }

        suspend fun withScheduleCommitSucceeding() = apply {
            everySuspend {
                pendingProposalScheduler.scheduleCommit(any(), any())
            }.returns(Unit)
        }

        suspend fun withGetConversationProtocolInfoSuccessful(protocolInfo: Conversation.ProtocolInfo) = apply {
            everySuspend {
                conversationRepository.getConversationProtocolInfo(any())
            }.returns(Either.Right(protocolInfo))
        }

        suspend fun withSubconversationInfo(groupId: GroupID?) = apply {
            everySuspend {
                subconversationRepository.getSubconversationInfo(any(), any())
            }.returns(groupId)
        }

        suspend fun withLocalGroupEpoch(result: Either<CoreFailure, ULong>) = apply {
            everySuspend {
                mlsConversationRepository.getLocalGroupEpoch(any(), any())
            }.returns(result)
        }

        fun withDecodedProtoContent(content: ProtoContent) = apply {
            every {
                protoContentMapper.decodeFromProtobuf(any<PlainMessageBlob>())
            }.returns(content)
        }

        fun arrange(block: suspend Arrangement.() -> Unit = {}) = let {
            runBlocking { block() }
            this to mlsMessageUnpacker
        }
    }

    companion object {
        val SELF_USER_ID = UserId("user-id", "domain")
        val DECRYPTED_COMMIT_BUNDLE = DecryptedMessageBundle.Commit(
            groupID = TestConversation.GROUP_ID,
            isActive = true,
            identity = null
        )

        fun decryptedProposal(commitDelay: Long?) = DecryptedMessageBundle.Proposal(
            groupID = TestConversation.GROUP_ID,
            commitDelay = commitDelay,
            identity = null
        )

        fun decryptedText(applicationMessage: ApplicationMessage) = DecryptedMessageBundle.Text(
            groupID = TestConversation.GROUP_ID,
            applicationMessage = applicationMessage,
            identity = null
        )
    }
}
