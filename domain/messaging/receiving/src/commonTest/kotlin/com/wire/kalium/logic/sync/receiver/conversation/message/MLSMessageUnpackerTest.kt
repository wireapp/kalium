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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.data.conversation.ApplicationMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSMessageDecryptor
import com.wire.kalium.logic.data.conversation.SubconversationGroupInfoProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentDecoder
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mls.ConversationProtocolGetter
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.sync.KaliumSyncException
import dev.mokkery.MockMode
import dev.mokkery.mock
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class MLSMessageUnpackerTest {

    @Test
    fun givenConversationWithProteusProtocol_whenUnpacking_thenFailWithNotSupportedByProteus() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withProtocolInfo(Conversation.ProtocolInfo.Proteus)
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldFail { assertEquals(CoreFailure.NotSupportedByProteus, it) }
    }

    @Test
    fun givenConversationWithMixedProtocol_whenUnpacking_thenSucceed() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withProtocolInfo(protocolInfo(mixed = true))
            .withDecryptResult(Either.Right(listOf(DECRYPTED_HANDSHAKE_BUNDLE)))
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldSucceed { assertEquals(listOf(MessageUnpackResult.HandshakeMessage), it) }
    }

    @Test
    fun givenConversationWithMLSProtocol_whenUnpacking_thenSucceed() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withProtocolInfo(protocolInfo())
            .withDecryptResult(Either.Right(listOf(DECRYPTED_HANDSHAKE_BUNDLE)))
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldSucceed { assertEquals(listOf(MessageUnpackResult.HandshakeMessage), it) }
    }

    @Test
    fun givenNewMLSMessageEventWithProposal_whenUnpacking_thenScheduleProposalTimer() = runTest {
        val commitDelay = 10L
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(listOf(proposalBundle(commitDelay))))
            .arrange()

        unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        assertEquals(listOf(GROUP_ID to MESSAGE_INSTANT.plus(commitDelay.seconds)), arrangement.scheduledCommits)
    }

    @Test
    fun givenNewMLSMessageEvent_whenUnpacking_thenDecryptMessage() = runTest {
        val decodedContent = "content".encodeToByteArray()
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(listOf(DECRYPTED_HANDSHAKE_BUNDLE)))
            .arrange()
        val event = newMlsMessageEvent(content = Base64.encode(decodedContent))

        unpacker.unpackMlsMessage(arrangement.mlsContext, event)

        assertEquals(1, arrangement.decryptedMessages.size)
        assertEquals(GROUP_ID, arrangement.decryptedMessages.single().second)
        assertEquals(true, decodedContent.contentEquals(arrangement.decryptedMessages.single().first))
    }

    @Test
    fun givenMappedSubconversation_whenUnpacking_thenLookupSubconversationBeforeDecryptingWithoutParentLookup() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withSubconversationGroup(SUBCONVERSATION_GROUP_ID)
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        unpacker.unpackMlsMessage(
            arrangement.mlsContext,
            newMlsMessageEvent(subconversationId = SUBCONVERSATION_ID)
        )

        assertEquals(listOf("subconversation", "decrypt:$SUBCONVERSATION_GROUP_ID"), arrangement.calls)
    }

    @Test
    fun givenMissingSubconversationMapping_whenUnpacking_thenFallBackToParentAfterSubconversationLookup() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withSubconversationGroup(null)
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        unpacker.unpackMlsMessage(
            arrangement.mlsContext,
            newMlsMessageEvent(subconversationId = SUBCONVERSATION_ID)
        )

        assertEquals(listOf("subconversation", "protocol", "decrypt:$GROUP_ID"), arrangement.calls)
    }

    @Test
    fun givenMalformedBase64_whenUnpacking_thenExceptionEscapesBeforeDecryptorCall() = runTest {
        val (arrangement, unpacker) = Arrangement().arrange()

        assertFailsWith<IllegalArgumentException> {
            unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent(content = "%%%"))
        }

        assertEquals(listOf("protocol"), arrangement.calls)
        assertEquals(emptyList(), arrangement.decryptedMessages)
    }

    @Test
    fun givenEmptyDecryptedBundleList_whenUnpacking_thenReturnExactlyOneHandshakeResult() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldSucceed { assertEquals(listOf(MessageUnpackResult.HandshakeMessage), it) }
    }

    @Test
    fun givenMultipleApplicationBundles_whenUnpacking_thenScheduleBeforeDecodeSequentiallyAndPreserveOutputOrder() = runTest {
        val firstGroup = GroupID("first-group")
        val secondGroup = GroupID("second-group")
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(
                Either.Right(
                    listOf(
                        proposalBundle(2L, firstGroup),
                        applicationBundle("first", firstGroup),
                        proposalBundle(4L, secondGroup),
                        applicationBundle("second", secondGroup)
                    )
                )
            )
            .withDecoder { blob -> readable(blob.data.decodeToString()) }
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        assertEquals(
            listOf(
                "protocol",
                "decrypt:$GROUP_ID",
                "schedule:first-group",
                "decode:first",
                "schedule:second-group",
                "decode:second"
            ),
            arrangement.calls
        )
        result.shouldSucceed { unpacked ->
            assertEquals(
                listOf("first", "second"),
                unpacked.filterIsInstance<MessageUnpackResult.ApplicationMessage>().map { it.content.messageUid }
            )
        }
    }

    @Test
    fun givenUnreadableMLSProtobuf_whenUnpacking_thenThrowSameKaliumSyncException() = runTest {
        val bundle = applicationBundle("external", GROUP_ID)
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(listOf(bundle)))
            .withDecoder {
                ProtoContent.ExternalMessageInstructions(
                    messageUid = "external",
                    otrKey = byteArrayOf(),
                    sha256 = null,
                    encryptionAlgorithm = null
                )
            }
            .arrange()

        val exception = assertFailsWith<KaliumSyncException> {
            unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())
        }

        assertEquals("MLS message with external content", exception.message)
        assertEquals(CoreFailure.Unknown(null), exception.coreFailureCause)
    }

    @Test
    fun givenBufferedFailure_whenUnpacking_thenQueryEpochForDiagnosticsAndReturnOriginalFailure() = runTest {
        listOf(MLSFailure.BufferedFutureMessage, MLSFailure.BufferedCommit).forEach { failure ->
            val (arrangement, unpacker) = Arrangement()
                .withDecryptResult(Either.Left(failure))
                .withEpochResult(Either.Right(7UL))
                .arrange()

            val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

            result.shouldFail { assertSame(failure, it) }
            assertEquals(listOf("protocol", "decrypt:$GROUP_ID", "epoch:$GROUP_ID"), arrangement.calls)
        }
    }

    @Test
    fun givenNonBufferedFailure_whenUnpacking_thenDoNotQueryEpochAndReturnOriginalFailure() = runTest {
        val failure = CoreFailure.Unknown(null)
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Left(failure))
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldFail { assertSame(failure, it) }
        assertEquals(listOf("protocol", "decrypt:$GROUP_ID"), arrangement.calls)
    }

    @Test
    fun givenEmptyDecryptedBundleList_whenUnpacking_thenReturnHandshakeMessage() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

        result.shouldSucceed {
            assertEquals(listOf(MessageUnpackResult.HandshakeMessage), it)
        }
    }

    @Test
    fun givenSubconversationGroupExists_whenUnpacking_thenDecryptUsingSubconversationGroup() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withSubconversationGroup(SUBCONVERSATION_GROUP_ID)
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        unpacker.unpackMlsMessage(
            arrangement.mlsContext,
            newMlsMessageEvent(subconversationId = SUBCONVERSATION_ID),
        )

        assertEquals(SUBCONVERSATION_GROUP_ID, arrangement.decryptedMessages.single().second)
        assertEquals(listOf("subconversation", "decrypt:$SUBCONVERSATION_GROUP_ID"), arrangement.calls)
    }

    @Test
    fun givenSubconversationGroupIsMissing_whenUnpacking_thenFallBackToParentConversationGroup() = runTest {
        val (arrangement, unpacker) = Arrangement()
            .withSubconversationGroup(null)
            .withDecryptResult(Either.Right(emptyList()))
            .arrange()

        unpacker.unpackMlsMessage(
            arrangement.mlsContext,
            newMlsMessageEvent(subconversationId = SUBCONVERSATION_ID),
        )

        assertEquals(GROUP_ID, arrangement.decryptedMessages.single().second)
    }

    @Test
    fun givenBufferedMlsFailure_whenUnpacking_thenReadEpochAndPropagateFailure() = runTest {
        listOf(MLSFailure.BufferedFutureMessage, MLSFailure.BufferedCommit).forEach { failure ->
            val (arrangement, unpacker) = Arrangement()
                .withDecryptResult(Either.Left(failure))
                .withEpochResult(Either.Right(42UL))
                .arrange()

            val result = unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())

            result.shouldFail { assertEquals(failure, it) }
            assertEquals(listOf("protocol", "decrypt:$GROUP_ID", "epoch:$GROUP_ID"), arrangement.calls)
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
            senderID = SENDER_USER_ID,
            senderClientID = SENDER_CLIENT_ID,
        )
        val (_, unpacker) = Arrangement()
            .withDecoder { readable }
            .arrange()

        val result = unpacker.unpackMlsBundle(
            applicationBundle(applicationMessage, GROUP_ID),
            CONVERSATION_ID,
            MESSAGE_INSTANT,
        )

        assertIs<MessageUnpackResult.ApplicationMessage>(result)
        assertEquals(readable, result.content)
        assertEquals(SENDER_USER_ID, result.senderUserId)
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
        val (_, unpacker) = Arrangement()
            .withDecoder { external }
            .arrange()

        assertFailsWith<KaliumSyncException> {
            unpacker.unpackMlsBundle(
                applicationBundle(ApplicationMessage(byteArrayOf(1), SENDER_USER_ID, SENDER_CLIENT_ID), GROUP_ID),
                CONVERSATION_ID,
                MESSAGE_INSTANT,
            )
        }
    }

    @Test
    fun givenSchedulerCancellation_whenUnpacking_thenCancellationEscapes() = runTest {
        val expected = CancellationException("cancelled")
        val (arrangement, unpacker) = Arrangement()
            .withDecryptResult(Either.Right(listOf(proposalBundle(1L))))
            .withSchedulerFailure(expected)
            .arrange()

        val actual = assertFailsWith<CancellationException> {
            unpacker.unpackMlsMessage(arrangement.mlsContext, newMlsMessageEvent())
        }

        assertSame(expected, actual)
    }

    private class Arrangement {
        val mlsContext = mock<MlsCoreCryptoContext>(mode = MockMode.autoUnit)
        val calls = mutableListOf<String>()
        val decryptedMessages = mutableListOf<Pair<ByteArray, GroupID>>()
        val scheduledCommits = mutableListOf<Pair<GroupID, Instant>>()

        private var protocolInfo: Either<CoreFailure, Conversation.ProtocolInfo> = Either.Right(protocolInfo())
        private var subconversationGroup: GroupID? = null
        private var decryptResult: Either<CoreFailure, List<DecryptedMessageBundle>> = Either.Right(emptyList())
        private var epochResult: Either<CoreFailure, ULong> = Either.Right(0UL)
        private var decoder: (PlainMessageBlob) -> ProtoContent = { readable(it.data.decodeToString()) }
        private var schedulerFailure: Throwable? = null

        fun withProtocolInfo(value: Conversation.ProtocolInfo) = apply { protocolInfo = Either.Right(value) }
        fun withSubconversationGroup(value: GroupID?) = apply { subconversationGroup = value }
        fun withDecryptResult(value: Either<CoreFailure, List<DecryptedMessageBundle>>) = apply { decryptResult = value }
        fun withEpochResult(value: Either<CoreFailure, ULong>) = apply { epochResult = value }
        fun withDecoder(value: (PlainMessageBlob) -> ProtoContent) = apply { decoder = value }
        fun withSchedulerFailure(value: Throwable) = apply { schedulerFailure = value }

        fun arrange(): Pair<Arrangement, MLSMessageUnpacker> {
            val protocolGetter = ConversationProtocolGetter {
                calls += "protocol"
                protocolInfo
            }
            val subconversationProvider = SubconversationGroupInfoProvider { _, _ ->
                calls += "subconversation"
                subconversationGroup
            }
            val decryptor = object : MLSMessageDecryptor {
                override suspend fun decryptMessage(
                    mlsContext: MlsCoreCryptoContext,
                    message: ByteArray,
                    groupID: GroupID
                ): Either<CoreFailure, List<DecryptedMessageBundle>> {
                    calls += "decrypt:$groupID"
                    decryptedMessages += message to groupID
                    return decryptResult
                }

                override suspend fun getLocalGroupEpoch(
                    mlsContext: MlsCoreCryptoContext,
                    groupID: GroupID
                ): Either<CoreFailure, ULong> {
                    calls += "epoch:$groupID"
                    return epochResult
                }
            }
            val scheduler = object : PendingProposalScheduler {
                override suspend fun scheduleCommit(groupID: GroupID, date: Instant) {
                    calls += "schedule:${groupID.value}"
                    schedulerFailure?.let { throw it }
                    scheduledCommits += groupID to date
                }
            }
            val protoContentDecoder = object : ProtoContentDecoder {
                override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
                    calls += "decode:${encodedContent.data.decodeToString()}"
                    return decoder(encodedContent)
                }
            }
            return this to MLSMessageUnpackerImpl(
                conversationProtocolGetter = protocolGetter,
                subconversationGroupInfoProvider = subconversationProvider,
                mlsMessageDecryptor = decryptor,
                pendingProposalScheduler = scheduler,
                protoContentDecoder = protoContentDecoder,
            )
        }
    }

    private companion object {
        val CONVERSATION_ID = ConversationId("conversation", "example.com")
        val SENDER_USER_ID = UserId("sender", "example.com")
        val SENDER_CLIENT_ID = ClientId("client")
        val GROUP_ID = GroupID("group")
        val SUBCONVERSATION_ID = SubconversationId("subconversation")
        val SUBCONVERSATION_GROUP_ID = GroupID("subconversation-group")
        val MESSAGE_INSTANT = Instant.fromEpochSeconds(1_700_000_000)
        val DECRYPTED_HANDSHAKE_BUNDLE = DecryptedMessageBundle.Commit(
            groupID = GROUP_ID,
            isActive = true,
            identity = null
        )

        fun newMlsMessageEvent(
            content: String = Base64.encode("content".encodeToByteArray()),
            subconversationId: SubconversationId? = null,
        ) = Event.Conversation.NewMLSMessage(
            id = "event",
            conversationId = CONVERSATION_ID,
            subconversationId = subconversationId,
            senderUserId = SENDER_USER_ID,
            messageInstant = MESSAGE_INSTANT,
            content = content,
        )

        fun protocolInfo(mixed: Boolean = false): Conversation.ProtocolInfo = if (mixed) {
            Conversation.ProtocolInfo.Mixed(
                groupId = GROUP_ID,
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                epoch = 1UL,
                keyingMaterialLastUpdate = Instant.DISTANT_PAST,
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        } else {
            Conversation.ProtocolInfo.MLS(
                groupId = GROUP_ID,
                groupState = Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED,
                epoch = 1UL,
                keyingMaterialLastUpdate = Instant.DISTANT_PAST,
                cipherSuite = CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519
            )
        }

        fun applicationBundle(content: String, groupID: GroupID) = applicationBundle(
            ApplicationMessage(
                message = content.encodeToByteArray(),
                senderID = SENDER_USER_ID,
                senderClientID = SENDER_CLIENT_ID
            ),
            groupID
        )

        fun applicationBundle(applicationMessage: ApplicationMessage, groupID: GroupID) = DecryptedMessageBundle.Text(
            groupID = groupID,
            applicationMessage = applicationMessage,
            identity = null
        )

        fun proposalBundle(commitDelay: Long?, groupID: GroupID = GROUP_ID) = DecryptedMessageBundle.Proposal(
            groupID = groupID,
            commitDelay = commitDelay,
            identity = null
        )

        fun readable(messageUid: String) = ProtoContent.Readable(
            messageUid = messageUid,
            messageContent = MessageContent.Unknown(),
            expectsReadConfirmation = false,
            legalHoldStatus = Conversation.LegalHoldStatus.DISABLED
        )
    }
}

private inline fun <L, R> Either<L, R>.shouldSucceed(assertion: (R) -> Unit = {}) {
    when (this) {
        is Either.Left -> fail("Expected success but got $value")
        is Either.Right -> assertion(value)
    }
}

private inline fun <L, R> Either<L, R>.shouldFail(assertion: (L) -> Unit = {}) {
    when (this) {
        is Either.Left -> assertion(value)
        is Either.Right -> fail("Expected failure but got $value")
    }
}
