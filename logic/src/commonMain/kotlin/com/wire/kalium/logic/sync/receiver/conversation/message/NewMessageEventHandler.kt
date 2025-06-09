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
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.typeDescription
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.SubconversationId
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.EventProcessingLogger
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.serialization.toJsonElement

internal interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage, deliveryInfo: EventDeliveryInfo)
    suspend fun handleNewMLSBatch(event: Event.Conversation.MLSGroupMessages, deliveryInfo: EventDeliveryInfo)
    suspend fun handleNewMLSSubGroupBatch(event: Event.Conversation.MLSSubGroupMessages, deliveryInfo: EventDeliveryInfo)

}

@Suppress("LongParameterList")
internal class NewMessageEventHandlerImpl(
    private val proteusMessageUnpacker: ProteusMessageUnpacker,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val legalHoldHandler: LegalHoldHandler,
    private val enqueueSelfDeletion: (conversationId: ConversationId, messageId: String) -> Unit,
    private val enqueueConfirmationDelivery: suspend (conversationId: ConversationId, messageId: String) -> Unit,
    private val selfUserId: UserId,
    private val staleEpochVerifier: StaleEpochVerifier
) : NewMessageEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage, deliveryInfo: EventDeliveryInfo) {
        val eventLogger = logger.createEventProcessingLogger(event)
        proteusMessageUnpacker.unpackProteusMessage(event) {
            processApplicationMessage(it, deliveryInfo)
            it
        }.onSuccess {
            eventLogger.logSuccess(
                "protocol" to "Proteus",
                "messageType" to it.messageTypeDescription,
            )
        }.onFailure {
            val logMap = mapOf(
                "event" to event.toLogMap(),
                "errorInfo" to "$it",
                "protocol" to "Proteus"
            )

            if (it is ProteusFailure && it.proteusException.code == ProteusException.Code.DUPLICATE_MESSAGE) {
                logger.i("Ignoring duplicate event: ${logMap.toJsonElement()}")
                return
            }

            logger.e("Failed to decrypt event: ${logMap.toJsonElement()}")

            val errorCode = if (it is ProteusFailure) it.proteusException.intCode else null

            applicationMessageHandler.handleDecryptionError(
                eventId = event.id,
                conversationId = event.conversationId,
                messageInstant = event.messageInstant,
                senderUserId = event.senderUserId,
                senderClientId = event.senderClientId,
                content = MessageContent.FailedDecryption(
                    encodedData = event.encryptedExternalContent?.data,
                    errorCode = errorCode,
                    isDecryptionResolved = false,
                    senderUserId = event.senderUserId,
                    clientId = ClientId(event.senderClientId.value)
                )
            )
            eventLogger.logFailure(it, "protocol" to "Proteus")
        }
    }

    override suspend fun handleNewMLSBatch(event: Event.Conversation.MLSGroupMessages, deliveryInfo: EventDeliveryInfo) {
        var eventLogger = logger.createEventProcessingLogger(event)

        mlsMessageUnpacker.unpackMlsGroupMessages(event)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    null
                )
            }.onSuccess { batchResult ->
                // TODO KBX
//                 batchResult.forEach { unpackResult ->
//                     when (unpackResult) {
//                         is MessageUnpackResult.ApplicationMessage -> {
//                             processApplicationMessage(unpackResult, deliveryInfo)
//                         }
//
//                         is MessageUnpackResult.FailedMessage -> {
//                             when (MLSMessageFailureHandler.handleFailure(unpackResult.error)) {
//                                 is MLSMessageFailureResolution.Ignore -> {
//                                     eventLogger.logFailure(unpackResult.error, "protocol" to "MLS", "mlsOutcome" to "IGNORE")
//                                 }
//
//                                 is MLSMessageFailureResolution.InformUser -> {
//                                     eventLogger.logFailure(unpackResult.error, "protocol" to "MLS", "mlsOutcome" to "INFORM_USER")
//                                     event.messages.firstOrNull { it.id == unpackResult.eventId }?.let { mlsMessage ->
//                                         applicationMessageHandler.handleDecryptionError(
//                                             eventId = mlsMessage.id,
//                                             conversationId = event.conversationId,
//                                             messageInstant = mlsMessage.messageInstant,
//                                             senderUserId = mlsMessage.senderUserId,
//                                             senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
//                                             content = MessageContent.FailedDecryption(
//                                                 isDecryptionResolved = false,
//                                                 senderUserId = mlsMessage.senderUserId
//                                             )
//                                         )
//                                     }
//                                 }
//
//                                 is MLSMessageFailureResolution.OutOfSync -> {
//                                     eventLogger.logFailure(unpackResult.error, "protocol" to "MLS", "mlsOutcome" to "OUT_OF_SYNC")
//                                     staleEpochVerifier.verifyEpoch(
//                                         event.conversationId,
//                                         null,
//                                     )
//                                 }
//                             }
//                         }
//
//                         MessageUnpackResult.HandshakeMessage -> {}
//                     }
//
//                     if (unpackResult !is MessageUnpackResult.FailedMessage) {
//                         eventLogger.logSuccess(
//                             "protocol" to "MLS",
//                             "isPartOfMLSBatch" to (batchResult.size > 1),
//                             "messageType" to unpackResult.messageTypeDescription
//                         )
//                     }
//
//                     // reset the time for next messages, if this is a batch
//                     eventLogger = logger.createEventProcessingLogger(event)
//                 }
            }
    }

    override suspend fun handleNewMLSSubGroupBatch(event: Event.Conversation.MLSSubGroupMessages, deliveryInfo: EventDeliveryInfo) {
        var eventLogger: EventProcessingLogger = logger.createEventProcessingLogger(event)

        mlsMessageUnpacker.unpackMlsSubGroupMessages(event)
            .onFailure {
                handleMLSFailure(
                    eventLogger,
                    it,
                    event.conversationId,
                    event.subConversationId
                )
            }.onSuccess { batchResult ->
                // TODO KBX
//                 batchResult.forEach { unpackResult ->
//                     if (unpackResult is MessageUnpackResult.ApplicationMessage) {
//                         processApplicationMessage(unpackResult, deliveryInfo)
//                     }
//                     if (unpackResult !is MessageUnpackResult.FailedMessage) {
//                         eventLogger.logSuccess(
//                             "protocol" to "MLS",
//                             "isPartOfMLSBatch" to (batchResult.size > 1),
//                             "messageType" to unpackResult.messageTypeDescription
//                         )
//                     }
//                     // reset the time for next messages, if this is a batch
//                     eventLogger = logger.createEventProcessingLogger(event)
//                 }
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

    private suspend fun processApplicationMessage(
        it: MessageUnpackResult.ApplicationMessage,
        deliveryInfo: EventDeliveryInfo
    ) {
        if (it.content.legalHoldStatus != Conversation.LegalHoldStatus.UNKNOWN) {
            legalHoldHandler.handleNewMessage(it, isLive = deliveryInfo.source == EventSource.LIVE)
        }
        handleSuccessfulResult(it)
        onMessageInserted(it)
    }

    private val MessageUnpackResult.messageTypeDescription
        get() = when (this) {
            is MessageUnpackResult.ApplicationMessage -> content.messageContent.typeDescription()
            is MessageUnpackResult.FailedMessage -> "Failed message"
            MessageUnpackResult.HandshakeMessage -> "Handshake message"
        }

    private suspend fun onMessageInserted(result: MessageUnpackResult.ApplicationMessage) {
        if (result.senderUserId != selfUserId && result.content.messageContent is MessageContent.Regular) {
            enqueueConfirmationDelivery(result.conversationId, result.content.messageUid)
        }

        if (result.senderUserId == selfUserId && result.content.expiresAfterMillis != null) {
            enqueueSelfDeletion(
                result.conversationId,
                result.content.messageUid
            )
        }
    }

    private suspend fun handleSuccessfulResult(result: MessageUnpackResult.ApplicationMessage) {
        applicationMessageHandler.handleContent(
            conversationId = result.conversationId,
            messageInstant = result.instant,
            senderUserId = result.senderUserId,
            senderClientId = result.senderClientId,
            content = result.content
        )
    }
}
