/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FailedMLSMessage
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.SubconversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.typeDescription
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.EventProcessingLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import io.mockative.Mockable

@Mockable
internal interface MLSBatchHandler {
    suspend fun handleNewMLSBatch(event: Event.Conversation.MLSGroupMessages, deliveryInfo: EventDeliveryInfo)
    suspend fun handleNewMLSSubGroupBatch(event: Event.Conversation.MLSSubGroupMessages, deliveryInfo: EventDeliveryInfo)

}

@Suppress("LongParameterList")
internal class MLSBatchHandlerImpl(
    private val mlsConversationRepository: MLSConversationRepository,
    private val conversationRepository: ConversationRepository,
    private val subconversationRepository: SubconversationRepository,
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val staleEpochVerifier: StaleEpochVerifier
) : MLSBatchHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewMLSBatch(event: Event.Conversation.MLSGroupMessages, deliveryInfo: EventDeliveryInfo) {
        var eventLogger = logger.createEventProcessingLogger(event)
        messagesFromMLSGroupMessages(event)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    null
                )
            }.onSuccess { batchResult ->
                batchResult?.let { failedMessage ->
                    when (MLSMessageFailureHandler.handleFailure(failedMessage.error)) {
                        is MLSMessageFailureResolution.Ignore -> {
                            eventLogger.logFailure(failedMessage.error, "protocol" to "MLS", "mlsOutcome" to "IGNORE")
                        }

                        is MLSMessageFailureResolution.InformUser -> {
                            eventLogger.logFailure(failedMessage.error, "protocol" to "MLS", "mlsOutcome" to "INFORM_USER")
                            event.messages.firstOrNull { it.id == failedMessage.eventId }?.let { mlsMessage ->
                                applicationMessageHandler.handleDecryptionError(
                                    eventId = mlsMessage.id,
                                    conversationId = event.conversationId,
                                    messageInstant = mlsMessage.messageInstant,
                                    senderUserId = mlsMessage.senderUserId,
                                    senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                                    content = MessageContent.FailedDecryption(
                                        isDecryptionResolved = false,
                                        senderUserId = mlsMessage.senderUserId
                                    )
                                )
                            }
                        }

                        is MLSMessageFailureResolution.OutOfSync -> {
                            eventLogger.logFailure(failedMessage.error, "protocol" to "MLS", "mlsOutcome" to "OUT_OF_SYNC")
                            staleEpochVerifier.verifyEpoch(
                                event.conversationId,
                                null,
                            )
                        }
                    }
                } ?: {
                    eventLogger.logSuccess(
                        "protocol" to "MLS",
                        "isPartOfMLSBatch" to (event.messages.size > 1),

                        )
                }
                // reset the time for next messages, if this is a batch
                eventLogger = logger.createEventProcessingLogger(event)
            }
    }

    override suspend fun handleNewMLSSubGroupBatch(event: Event.Conversation.MLSSubGroupMessages, deliveryInfo: EventDeliveryInfo) {
        var eventLogger: EventProcessingLogger = logger.createEventProcessingLogger(event)

        messagesFromMLSSubGroupMessages(event)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    event.subConversationId
                )
            }.onSuccess {
                // TODO do we need to log failures here
                // reset the time for next messages, if this is a batch
                eventLogger = logger.createEventProcessingLogger(event)
            }
    }

private suspend fun handleMLSFailure(
    eventLogger: EventProcessingLogger,
    failure: CoreFailure,
    conversationId: ConversationId,
    subConversationId: SubconversationId?
) {
    when (MLSMessageFailureHandler.handleFailure(failure)) {
        is MLSMessageFailureResolution.Ignore -> {
            eventLogger.logFailure(failure, "protocol" to "MLS", "mlsOutcome" to "IGNORE")
        }

        is MLSMessageFailureResolution.InformUser -> {
            eventLogger.logFailure(failure, "protocol" to "MLS", "mlsOutcome" to "INFORM_USER")
        }

        is MLSMessageFailureResolution.OutOfSync -> {
            eventLogger.logFailure(failure, "protocol" to "MLS", "mlsOutcome" to "OUT_OF_SYNC")
            staleEpochVerifier.verifyEpoch(
                conversationId,
                subConversationId,
            )
        }
    }
}


private val MessageUnpackResult.messageTypeDescription
    get() = when (this) {
        is MessageUnpackResult.ApplicationMessage -> content.messageContent.typeDescription()
        MessageUnpackResult.HandshakeMessage -> "Handshake message"
    }


private suspend fun messagesFromMLSGroupMessages(
    event: Event.Conversation.MLSGroupMessages
): Either<CoreFailure, FailedMLSMessage?> = conversationRepository.getConversationProtocolInfo(event.conversationId)
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
                protocolInfo.groupId,
                event.conversationId,
                null
            )
        } else {
            Either.Left(CoreFailure.NotSupportedByProteus)
        }
    }

private suspend fun messagesFromMLSSubGroupMessages(
    event: Event.Conversation.MLSSubGroupMessages
): Either<CoreFailure, FailedMLSMessage?> =
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
            mlsConversationRepository.decryptMessages(event.messages, groupID, event.conversationId, event.subConversationId)
        } ?: Either.Right(null)
}
