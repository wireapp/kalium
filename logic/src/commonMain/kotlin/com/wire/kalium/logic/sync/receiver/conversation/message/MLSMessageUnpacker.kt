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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.ApplicationMessage
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.E2EIdentity
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.wrapMLSRequest
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds

internal interface MLSMessageUnpacker {
    suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, MessageUnpackResult>
}

@Suppress("LongParameterList")
internal class MLSMessageUnpackerImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val conversationRepository: ConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val epochsFlow: MutableSharedFlow<GroupID>,
    private val selfUserId: UserId,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(selfUserId = selfUserId),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : MLSMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, MessageUnpackResult> =
        messageFromMLSMessage(event).map { bundle ->
            if (bundle == null) return@map MessageUnpackResult.HandshakeMessage

            bundle.commitDelay?.let {
                handlePendingProposal(
                    timestamp = event.timestampIso.toInstant(),
                    groupId = bundle.groupID,
                    commitDelay = it
                )
            }

            bundle.applicationMessage?.let {
                val protoContent = protoContentMapper.decodeFromProtobuf(PlainMessageBlob(it.message))
                if (protoContent !is ProtoContent.Readable) {
                    throw KaliumSyncException("MLS message with external content", CoreFailure.Unknown(null))
                }
                MessageUnpackResult.ApplicationMessage(
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = it.senderClientID,
                    content = protoContent
                )
            } ?: MessageUnpackResult.HandshakeMessage
        }

    private suspend fun handlePendingProposal(timestamp: Instant, groupId: GroupID, commitDelay: Long) {
        logger.d("Received MLS proposal, scheduling commit in $commitDelay seconds")
        pendingProposalScheduler.scheduleCommit(
            groupId,
            timestamp.plus(commitDelay.seconds)
        )
    }

    private suspend fun messageFromMLSMessage(
        messageEvent: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, DecryptedMessageBundle?> =
        messageEvent.subconversationId?.let { subconversationId ->
            subconversationRepository.getSubconversationInfo(messageEvent.conversationId, subconversationId)?.let { groupID ->
                logger.d(
                    "Decrypting MLS for " +
                            "converationId = ${messageEvent.conversationId.value.obfuscateId()} " +
                            "subconversationId = $subconversationId " +
                            "groupID = ${groupID.value.obfuscateId()}"
                )
                decryptMessageContent(messageEvent.content.decodeBase64Bytes(), groupID)
            }
        } ?: conversationRepository.getConversationProtocolInfo(messageEvent.conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                logger.d(
                    "Decrypting MLS for " +
                            "converationId = ${messageEvent.conversationId.value.obfuscateId()} " +
                            "groupID = ${protocolInfo.groupId.value.obfuscateId()}"
                )
                decryptMessageContent(messageEvent.content.decodeBase64Bytes(), protocolInfo.groupId)
            } else {
                Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    private suspend fun decryptMessageContent(encryptedContent: ByteArray, groupID: GroupID): Either<CoreFailure, DecryptedMessageBundle?> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.decryptMessage(
                    idMapper.toCryptoModel(groupID),
                    encryptedContent
                ).let { it ->
                    if (it.hasEpochChanged) {
                        logger.d("Epoch changed for groupID = ${groupID.value.obfuscateId()}")
                        epochsFlow.emit(groupID)
                    }
                    DecryptedMessageBundle(
                        groupID,
                        it.message?.let { message ->
                            // We will always have senderClientId together with an application message
                            // but CoreCrypto API doesn't express this
                            val senderClientId = it.senderClientId?.let { senderClientId ->
                                idMapper.fromCryptoQualifiedClientId(senderClientId)
                            } ?: ClientId("")

                            ApplicationMessage(
                                message,
                                senderClientId
                            )
                        },
                        it.commitDelay,
                        identity = it.identity?.let { identity ->
                            E2EIdentity(
                                identity.clientId,
                                identity.handle,
                                identity.displayName,
                                identity.domain
                            )
                        }
                    )
                }
            }
        }
}
