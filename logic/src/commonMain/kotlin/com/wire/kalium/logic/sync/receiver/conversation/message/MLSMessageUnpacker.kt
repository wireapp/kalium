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

import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.logStructuredJson
import com.wire.kalium.logic.sync.KaliumSyncException
import io.ktor.util.decodeBase64Bytes
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

internal interface MLSMessageUnpacker {
    suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, List<MessageUnpackResult>>
    suspend fun unpackMlsBundle(
        bundle: DecryptedMessageBundle,
        conversationId: ConversationId,
        messageInstant: Instant
    ): MessageUnpackResult
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

    override suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, List<MessageUnpackResult>> =
        messageFromMLSMessage(event).map { bundles ->
            if (bundles.isEmpty()) return@map listOf(MessageUnpackResult.HandshakeMessage)

            bundles.map { bundle ->
                unpackMlsBundle(bundle, event.conversationId, event.messageInstant)
            }
        }

    override suspend fun unpackMlsBundle(
        bundle: DecryptedMessageBundle,
        conversationId: ConversationId,
        messageInstant: Instant
    ): MessageUnpackResult {
        bundle.commitDelay?.let {
            handlePendingProposal(
                timestamp = messageInstant,
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
                instant = messageInstant,
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

    private suspend fun messageFromMLSMessage(
        messageEvent: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<DecryptedMessageBundle>> =
        messageEvent.subconversationId?.let { subConversationId ->
            subconversationRepository.getSubconversationInfo(messageEvent.conversationId, subConversationId)?.let { groupID ->
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG, "Decrypting MLS for SubConversation", mapOf(
                        "conversationId" to messageEvent.conversationId.toLogString(),
                        "subConversationId" to subConversationId.toLogString(),
                        "groupID" to groupID.toLogString()
                    )
                )
                mlsConversationRepository.decryptMessage(messageEvent.content.decodeBase64Bytes(), groupID)
            }
        } ?: conversationRepository.getConversationProtocolInfo(messageEvent.conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG, "Decrypting MLS for Conversation", mapOf(
                        "conversationId" to messageEvent.conversationId.toLogString(),
                        "groupID" to protocolInfo.groupId.toLogString(),
                        "protocolInfo" to protocolInfo.toLogMap()
                    )
                )
                mlsConversationRepository.decryptMessage(messageEvent.content.decodeBase64Bytes(), protocolInfo.groupId)
            } else {
                Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }
}
