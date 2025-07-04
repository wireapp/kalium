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
import com.wire.kalium.cryptography.MlsCoreCryptoContext
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
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.util.EventProcessingLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import io.mockative.Mockable

@Mockable
internal interface MLSBatchHandler {
    suspend fun handleNewMLSBatch(
        event: Event.Conversation.MLSGroupMessages,
        deliveryInfo: EventDeliveryInfo,
        mlsContext: MlsCoreCryptoContext?
    )

    suspend fun handleNewMLSSubGroupBatch(
        event: Event.Conversation.MLSSubGroupMessages,
        deliveryInfo: EventDeliveryInfo,
        mlsContext: MlsCoreCryptoContext?
    )
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

    override suspend fun handleNewMLSBatch(
        event: Event.Conversation.MLSGroupMessages,
        deliveryInfo: EventDeliveryInfo,
        mlsContext: MlsCoreCryptoContext?
    ) {
        var eventLogger = logger.createEventProcessingLogger(event)
        messagesFromMLSGroupMessages(event, deliveryInfo.source == EventSource.LIVE, mlsContext)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    null,
                    mlsContext
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

                            // TODO KBX all mls client related things need to be pushed by context
                            staleEpochVerifier.verifyEpoch(
                                event.conversationId,
                                null,
                                mlsContext
                            )
                        }
                    }
                } ?: {
                    eventLogger.logSuccess(
                        "protocol" to "MLS",
                        "isPartOfMLSBatch" to (event.messages.size > 1)
                    )
                }
                // reset the time for next messages, if this is a batch
                eventLogger = logger.createEventProcessingLogger(event)
            }
    }

    override suspend fun handleNewMLSSubGroupBatch(
        event: Event.Conversation.MLSSubGroupMessages,
        deliveryInfo: EventDeliveryInfo,
        mlsContext: MlsCoreCryptoContext?
    ) {
        var eventLogger: EventProcessingLogger = logger.createEventProcessingLogger(event)

        messagesFromMLSSubGroupMessages(event, deliveryInfo.source == EventSource.LIVE, mlsContext)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    event.subConversationId,
                    mlsContext
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
        subConversationId: SubconversationId?,
        mlsContext: MlsCoreCryptoContext?
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
                    mlsContext
                )
            }
        }
    }

    private suspend fun messagesFromMLSGroupMessages(
        event: Event.Conversation.MLSGroupMessages,
        isLive: Boolean,
        mlsContext: MlsCoreCryptoContext?
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
                    messages = event.messages,
                    groupID = protocolInfo.groupId,
                    conversationId = event.conversationId,
                    subConversationId = null,
                    isLive = isLive,
                    mlsContext = mlsContext

                )
            } else {
                Either.Left(CoreFailure.NotSupportedByProteus)
            }
        }

    private suspend fun messagesFromMLSSubGroupMessages(
        event: Event.Conversation.MLSSubGroupMessages,
        isLive: Boolean,
        mlsContext: MlsCoreCryptoContext?
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
                mlsConversationRepository.decryptMessages(
                    messages = event.messages,
                    groupID = groupID,
                    conversationId = event.conversationId,
                    subConversationId = event.subConversationId,
                    isLive = isLive,
                    mlsContext = mlsContext
                )
            } ?: Either.Right(null)
}
