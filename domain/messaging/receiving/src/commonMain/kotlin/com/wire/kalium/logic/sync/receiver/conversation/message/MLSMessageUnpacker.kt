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
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSMessageDecryptor
import com.wire.kalium.logic.data.conversation.SubconversationGroupInfoProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentDecoder
import com.wire.kalium.logic.data.mls.ConversationProtocolGetter
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64
import kotlin.time.Duration.Companion.seconds

@InternalKaliumApi
public interface MLSMessageUnpacker {
    public suspend fun unpackMlsMessage(
        mlsContext: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<MessageUnpackResult>>

    public suspend fun unpackMlsBundle(
        bundle: DecryptedMessageBundle,
        conversationId: ConversationId,
        messageInstant: Instant
    ): MessageUnpackResult
}

@InternalKaliumApi
public class MLSMessageUnpackerImpl public constructor(
    private val conversationProtocolGetter: ConversationProtocolGetter,
    private val subconversationGroupInfoProvider: SubconversationGroupInfoProvider,
    private val mlsMessageDecryptor: MLSMessageDecryptor,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val protoContentDecoder: ProtoContentDecoder,
) : MLSMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    public override suspend fun unpackMlsMessage(
        mlsContext: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<MessageUnpackResult>> = messageFromMLSMessage(mlsContext, event)
        .map { bundles ->
            if (bundles.isEmpty()) return@map listOf(MessageUnpackResult.HandshakeMessage)
            bundles.map { bundle ->
                unpackMlsBundle(bundle, event.conversationId, event.messageInstant)
            }
        }

    public override suspend fun unpackMlsBundle(
        bundle: DecryptedMessageBundle,
        conversationId: ConversationId,
        messageInstant: Instant
    ): MessageUnpackResult = when (bundle) {
        is DecryptedMessageBundle.Text -> {
            val protoContent = protoContentDecoder.decodeFromProtobuf(PlainMessageBlob(bundle.applicationMessage.message))
            if (protoContent !is ProtoContent.Readable) {
                throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
            }
            MessageUnpackResult.ApplicationMessage(
                conversationId = conversationId,
                instant = messageInstant,
                senderUserId = bundle.applicationMessage.senderID,
                senderClientId = bundle.applicationMessage.senderClientID,
                content = protoContent
            )
        }

        is DecryptedMessageBundle.Proposal -> {
            bundle.commitDelay?.let {
                handlePendingProposal(
                    timestamp = messageInstant,
                    groupId = bundle.groupID,
                    commitDelay = it
                )
            }
            MessageUnpackResult.HandshakeMessage
        }

        is DecryptedMessageBundle.Commit -> MessageUnpackResult.HandshakeMessage
    }

    private suspend fun handlePendingProposal(timestamp: Instant, groupId: GroupID, commitDelay: Long) {
        logger.logStructuredJson(
            KaliumLogLevel.DEBUG,
            "Received MLS proposal, scheduling delayed commit",
            mapOf(
                "groupId" to groupId.toLogString(),
                "commitDelay" to "$commitDelay"
            )
        )
        pendingProposalScheduler.scheduleCommit(
            groupId,
            timestamp.plus(commitDelay.seconds)
        )
    }

    private suspend fun messageFromMLSMessage(
        mlsContext: MlsCoreCryptoContext,
        messageEvent: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<DecryptedMessageBundle>> =
        messageEvent.subconversationId?.let { subConversationId ->
            subconversationGroupInfoProvider.getSubconversationInfo(messageEvent.conversationId, subConversationId)?.let { groupID ->
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG, "Decrypting MLS for SubConversation",
                        mapOf(
                        "conversationId" to messageEvent.conversationId.toLogString(),
                        "subConversationId" to subConversationId.toLogString(),
                        "groupID" to groupID.toLogString()
                    )
                )
                decryptMessageAndLogIfBuffered(mlsContext, messageEvent, groupID)
            }
        } ?: conversationProtocolGetter.getConversationProtocolInfo(messageEvent.conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG, "Decrypting MLS for Conversation",
                        mapOf(
                        "conversationId" to messageEvent.conversationId.toLogString(),
                        "groupID" to protocolInfo.groupId.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap()
                    )
                )
                decryptMessageAndLogIfBuffered(mlsContext, messageEvent, protocolInfo.groupId)
            } else {
                Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    private suspend fun decryptMessageAndLogIfBuffered(
        mlsContext: MlsCoreCryptoContext,
        messageEvent: Event.Conversation.NewMLSMessage,
        groupId: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>> {
        val result = mlsMessageDecryptor.decryptMessage(mlsContext, Base64.decode(messageEvent.content), groupId)
        val bufferType = when ((result as? Either.Left)?.value) {
            MLSFailure.BufferedFutureMessage -> "FUTURE_MESSAGE"
            MLSFailure.BufferedCommit -> "COMMIT"
            else -> return result
        }
        val localEpoch = mlsMessageDecryptor.getLocalGroupEpoch(mlsContext, groupId).getOrNull()
        logger.logStructuredJson(
            level = KaliumLogLevel.WARN,
            leadingMessage = "MLS message buffered",
            jsonStringKeyValues = buildMap {
                putAll(messageEvent.toLogMap())
                put("subConversationId", messageEvent.subconversationId?.toLogString())
                put("groupId", groupId.toLogString())
                put("localEpoch", localEpoch?.toString())
                put("bufferType", bufferType)
            }
        )
        return result
    }
}
