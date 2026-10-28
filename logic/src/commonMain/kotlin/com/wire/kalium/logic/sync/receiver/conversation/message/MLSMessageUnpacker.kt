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
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.DecryptedMessageBundle
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.conversation.proposalCommitDeadline
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.common.error.ServerError
import com.wire.kalium.common.error.isConversationNotFound
import kotlin.io.encoding.Base64
import kotlin.time.Instant

internal interface MLSMessageUnpacker {
    suspend fun unpackMlsMessage(
        mlsContext: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<MessageUnpackResult>>

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

    override suspend fun unpackMlsMessage(
        mlsContext: MlsCoreCryptoContext,
        event: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<MessageUnpackResult>> = messageFromMLSMessage(mlsContext, event)
        .map { bundles ->
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
        if (bundle is DecryptedMessageBundle.Proposal) {
            bundle.commitDelay?.let {
                handlePendingProposal(messageInstant, bundle.groupID, it)
            }
        }
        return bundle.unpack(conversationId, messageInstant, protoContentMapper)
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
            proposalCommitDeadline(timestamp, commitDelay)
        )
    }

    private suspend fun messageFromMLSMessage(
        mlsContext: MlsCoreCryptoContext,
        messageEvent: Event.Conversation.NewMLSMessage
    ): Either<CoreFailure, List<DecryptedMessageBundle>> {
        messageEvent.subconversationId?.let { subConversationId ->
            return resolveSubconversationGroup(mlsContext, messageEvent.conversationId, subConversationId).flatMap { groupID ->
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG,
                    "Decrypting MLS for SubConversation",
                    mapOf(
                        "conversationId" to messageEvent.conversationId.toLogString(),
                        "subConversationId" to subConversationId.toLogString(),
                        "groupID" to groupID.toLogString()
                    )
                )
                decryptMessageAndLogIfBuffered(mlsContext, messageEvent, groupID)
            }
        }
        return conversationRepository.getConversationProtocolInfo(messageEvent.conversationId).flatMap { protocolInfo ->
            if (protocolInfo is Conversation.ProtocolInfo.MLSCapable) {
                logger.logStructuredJson(
                    KaliumLogLevel.DEBUG,
                    "Decrypting MLS for Conversation",
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
    }

    @Suppress("ReturnCount", "ThrowsCount") // Keep terminal skips separate from failures that must retain the event.
    private suspend fun resolveSubconversationGroup(
        mlsContext: MlsCoreCryptoContext,
        conversationId: ConversationId,
        subconversationId: SubconversationId,
    ): Either<CoreFailure, GroupID> {
        subconversationRepository.getSubconversationInfo(conversationId, subconversationId)?.let {
            return Either.Right(it)
        }

        val remote = subconversationRepository.fetchRemoteSubConversationDetails(conversationId, subconversationId)
            .getOrFail { failure ->
                if (failure.isConversationNotFoundFailure()) return Either.Left(MLSFailure.ConversationNotFound)
                throw KaliumSyncException("Failed to recover subconversation mapping", failure)
            }
        if (remote.parentId != conversationId || remote.id != subconversationId) {
            throw KaliumSyncException("Subconversation identity mismatch", MLSFailure.InvalidGroupId)
        }

        val nativeGroupExists = wrapMLSRequest {
            mlsContext.conversationExists(remote.groupId.toCrypto())
        }.getOrFail { failure ->
            throw KaliumSyncException("Failed to inspect native subconversation state", failure)
        }
        // Leaving a subgroup removes its native state. Keep delayed events on the existing
        // ConversationNotFound/ignore path instead of attempting an external join.
        if (!nativeGroupExists) return Either.Left(MLSFailure.ConversationNotFound)

        subconversationRepository.insertSubconversation(conversationId, subconversationId, remote.groupId)
        logger.logStructuredJson(
            KaliumLogLevel.DEBUG,
            "Recovered MLS SubConversation mapping",
            mapOf(
                "conversationId" to conversationId.toLogString(),
                "subConversationId" to subconversationId.toLogString(),
                "groupID" to remote.groupId.toLogString(),
            )
        )
        return Either.Right(remote.groupId)
    }

    private suspend fun decryptMessageAndLogIfBuffered(
        mlsContext: MlsCoreCryptoContext,
        messageEvent: Event.Conversation.NewMLSMessage,
        groupId: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>> {
        val result = mlsConversationRepository.decryptMessage(mlsContext, Base64.decode(messageEvent.content), groupId)
        val bufferType = when ((result as? Either.Left)?.value) {
            MLSFailure.BufferedFutureMessage -> "FUTURE_MESSAGE"
            MLSFailure.BufferedCommit -> "COMMIT"
            else -> return result
        }
        val localEpoch = mlsConversationRepository.getLocalGroupEpoch(mlsContext, groupId).getOrNull()
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

private fun NetworkFailure.isConversationNotFoundFailure(): Boolean {
    val invalidRequest = (this as? NetworkFailure.ServerMiscommunication)
        ?.reason as? ServerError.InvalidRequest
    return invalidRequest?.isConversationNotFound() == true
}
