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

import com.wire.kalium.common.error.ProteusFailure
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.typeDescription
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.serialization.toJsonElement
import io.mockative.Mockable

@Mockable
internal interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage, deliveryInfo: EventDeliveryInfo)
    suspend fun handleNewMLSMessage(event: Event.Conversation.DecryptedMLSBatch, deliveryInfo: EventDeliveryInfo)
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

    override suspend fun handleNewMLSMessage(event: Event.Conversation.DecryptedMLSBatch, deliveryInfo: EventDeliveryInfo) {
        var eventLogger = logger.createEventProcessingLogger(event)
        mlsMessageUnpacker.unpackDecryptedMLSBatch(event).forEach { message ->
            if (message is MessageUnpackResult.ApplicationMessage) {
                processApplicationMessage(message, deliveryInfo)
            }
            eventLogger.logSuccess(
                "protocol" to "MLS",
                "isPartOfMLSBatch" to (event.messages.size > 1),
                "messageType" to message.messageTypeDescription
            )
            // reset the time for next messages, if this is a batch
            eventLogger = logger.createEventProcessingLogger(event)
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
