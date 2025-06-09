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
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.conversation.mls.MLSBatchResult
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.sync.KaliumSyncException
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

internal interface MLSMessageUnpacker {
    suspend fun unpackMlsGroupMessages(event: Event.Conversation.MLSGroupMessages): Either<CoreFailure, Unit>
    suspend fun unpackMlsSubGroupMessages(event: Event.Conversation.MLSSubGroupMessages): Either<CoreFailure, Unit>

}

@Suppress("LongParameterList")
internal class MLSMessageUnpackerImpl(
    private val conversationRepository: ConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
) : MLSMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun unpackMlsGroupMessages(
        event: Event.Conversation.MLSGroupMessages
    ): Either<CoreFailure, Unit>
//             List<MessageUnpackResult>>
    {
        // TODO KBX
        return Either.Right(Unit)
//         return messagesFromMLSGroupMessages(event).map { batch ->
//             if (batch.messages.isEmpty() && batch.failedMessage == null) return@map listOf(MessageUnpackResult.HandshakeMessage)
//
//             var results = batch.messages.map { bundle ->
//                 unpackMlsBundle(bundle.toModel(batch.groupId), event.conversationId)
//             }
//
//             batch.failedMessage?.let { failedMessage ->
//                 val failedResult = MessageUnpackResult.FailedMessage(
//                     failedMessage.eventId,
//                     failedMessage.error,
//                 )
//                 results = results + failedResult
//             }
//
//             results
//         }
    }

    override suspend fun unpackMlsSubGroupMessages(
        event: Event.Conversation.MLSSubGroupMessages
    ): Either<CoreFailure, Unit> {
        return Either.Right(Unit)
//         return messagesFromMLSSubGroupMessages(event).map { batch ->
//             if (batch == null || batch.messages.isEmpty()) return@map listOf(MessageUnpackResult.HandshakeMessage)
//
//             batch.messages.map { bundle ->
//                 unpackMlsBundle(bundle.toModel(batch.groupId), event.conversationId)
//             }
//         }
    }

    private suspend fun unpackMlsBundle(
        bundle: DecryptedMessageBundle,
        conversationId: ConversationId,
    ): MessageUnpackResult {
        bundle.commitDelay?.let {
            handlePendingProposal(
                timestamp = bundle.messageInstant,
                groupId = bundle.groupID,
                commitDelay = it
            )
        }

        return bundle.applicationMessage?.let {
            val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(it.message))
            if (protoContent !is ProtoContent.Readable) {
                throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
            }
            MessageUnpackResult.ApplicationMessage(
                conversationId = conversationId,
                instant = bundle.messageInstant,
                senderUserId = it.senderID,
                senderClientId = it.senderClientID,
                content = protoContent
            )
        } ?: MessageUnpackResult.HandshakeMessage
    }

    private suspend fun handlePendingProposal(timestamp: Instant, groupId: GroupID, commitDelay: Long) {
        logger.logStructuredJson(
            KaliumLogLevel.DEBUG, "Received MLS proposal, scheduling delayed commit",
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

    private suspend fun messagesFromMLSGroupMessages(
        event: Event.Conversation.MLSGroupMessages
    ): Either<CoreFailure, Unit> = conversationRepository.getConversationProtocolInfo(event.conversationId)
        .flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG,
                    "Decrypting MLS for Conversation",
                    mapOf(
                        "conversationId" to event.conversationId.toLogString(),
                        "groupID" to protocolInfo.groupId.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap()
                    )
                )
                mlsConversationRepository.decryptMessages(
                    event.messages,
                    protocolInfo.groupId
                )
            } else {
                Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    private suspend fun messagesFromMLSSubGroupMessages(
        event: Event.Conversation.MLSSubGroupMessages
    ): Either<CoreFailure, Unit?> =
        subconversationRepository.getSubconversationInfo(event.conversationId, event.subConversationId)
            ?.let { groupID ->
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG,
                    "Decrypting MLS for SubConversation",
                    mapOf(
                        "conversationId" to event.conversationId.toLogString(),
                        "subConversationId" to event.subConversationId.toLogString(),
                        "groupID" to groupID.toLogString()
                    )
                )
                mlsConversationRepository.decryptMessages(event.messages, groupID)
            } ?: Either.Right(null)
}
