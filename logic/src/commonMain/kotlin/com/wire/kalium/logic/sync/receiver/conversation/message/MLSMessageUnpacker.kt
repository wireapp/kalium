package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.*
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.feature.message.PendingProposalScheduler
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.KaliumSyncException
import com.wire.kalium.logic.wrapMLSRequest
import io.ktor.util.*
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.time.Duration.Companion.seconds

internal interface MLSMessageUnpacker {
    suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, MessageUnpackResult>
}

internal class MLSMessageUnpackerImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val conversationRepository: ConversationRepository,
    private val pendingProposalScheduler: PendingProposalScheduler,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
) : MLSMessageUnpacker {

    private val logger get() = kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)

    override suspend fun unpackMlsMessage(event: Event.Conversation.NewMLSMessage): Either<CoreFailure, MessageUnpackResult> =
        messageFromMLSMessage(event).map { bundle ->
            if (bundle == null) return@map MessageUnpackResult.ProtocolSignalingMessage

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
            } ?: MessageUnpackResult.ProtocolSignalingMessage
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
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            conversationRepository.getConversationById(messageEvent.conversationId)?.let { conversation ->
                if (conversation.protocol is Conversation.ProtocolInfo.MLS) {
                    val groupID = conversation.protocol.groupId
                    wrapMLSRequest {
                        mlsClient.decryptMessage(
                            idMapper.toCryptoModel(groupID),
                            messageEvent.content.decodeBase64Bytes()
                        ).let {
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
                                it.commitDelay
                            )
                        }
                    }
                } else {
                    Either.Right(null)
                }
            } ?: Either.Left(StorageFailure.DataNotFound)
        }
}
