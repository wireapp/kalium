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

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.common.error.CoreFailure
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
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.sync.incremental.EventSource
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.createEventProcessingLogger
import com.wire.kalium.util.serialization.toJsonElement
import io.mockative.Mockable

@Mockable
internal interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.NewMessage,
        deliveryInfo: EventDeliveryInfo
    )

    suspend fun handleNewMLSMessage(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.NewMLSMessage,
        deliveryInfo: EventDeliveryInfo
    )

    suspend fun flushPendingSideEffects()
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
    private val staleEpochVerifier: StaleEpochVerifier,
    private val resetMLSConversation: ResetMLSConversationUseCase,
) : NewMessageEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewProteusMessage(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.NewMessage,
        deliveryInfo: EventDeliveryInfo
    ) {
        val eventLogger = logger.createEventProcessingLogger(event)
        proteusMessageUnpacker.unpackProteusMessage(transactionContext.proteus, event) {
            processApplicationMessage(transactionContext, it, deliveryInfo)
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

            when (ProteusMessageFailureHandler.handleFailure(it)) {
                ProteusMessageFailureResolution.Ignore -> {
                    logger.i("Ignoring duplicate event: ${logMap.toJsonElement()}")
                    eventLogger.logFailure(it, "protocol" to "Proteus", "proteusOutcome" to "IGNORE")
                }

                ProteusMessageFailureResolution.RecoverSession -> {
                    logger.e("Proteus failure requires session recovery: ${logMap.toJsonElement()}")
                    eventLogger.logFailure(it, "protocol" to "Proteus", "proteusOutcome" to "RECOVER_SESSION")
                    persistProteusDecryptionFailure(
                        failure = it,
                        event = event,
                        isDecryptionResolved = false
                    )
                }

                ProteusMessageFailureResolution.InformUser -> {
                    logger.e("Failed to decrypt event: ${logMap.toJsonElement()}")
                    persistProteusDecryptionFailure(
                        failure = it,
                        event = event,
                        isDecryptionResolved = true
                    )
                    eventLogger.logFailure(it, "protocol" to "Proteus")
                }
            }
        }
    }

    private suspend fun persistProteusDecryptionFailure(
        failure: CoreFailure,
        event: Event.Conversation.NewMessage,
        isDecryptionResolved: Boolean
    ) {
        val errorCode = if (failure is ProteusFailure) failure.proteusException.intCode else null
        applicationMessageHandler.handleDecryptionError(
            eventId = event.id,
            conversationId = event.conversationId,
            messageInstant = event.messageInstant,
            senderUserId = event.senderUserId,
            senderClientId = event.senderClientId,
            content = MessageContent.FailedDecryption(
                encodedData = event.encryptedExternalContent?.data,
                errorCode = errorCode,
                isDecryptionResolved = isDecryptionResolved,
                senderUserId = event.senderUserId,
                clientId = ClientId(event.senderClientId.value)
            )
        )
    }

    override suspend fun handleNewMLSMessage(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.NewMLSMessage,
        deliveryInfo: EventDeliveryInfo
    ) {
        var eventLogger = logger.createEventProcessingLogger(event)
        transactionContext.wrapInMLSContext { mlsMessageUnpacker.unpackMlsMessage(it, event) }
            .onFailure {
                when (MLSMessageFailureHandler.handleFailure(it)) {
                    is MLSMessageFailureResolution.Ignore -> {
                        eventLogger.logFailure(it, "protocol" to "MLS", "mlsOutcome" to "IGNORE")
                    }

                    is MLSMessageFailureResolution.InformUser -> {
                        eventLogger.logFailure(it, "protocol" to "MLS", "mlsOutcome" to "INFORM_USER")
                        // messages from subconversations should not send a system message
                        if (event.subconversationId == null) {
                            applicationMessageHandler.handleDecryptionError(
                                eventId = event.id,
                                conversationId = event.conversationId,
                                messageInstant = event.messageInstant,
                                senderUserId = event.senderUserId,
                                senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                                content = MessageContent.FailedDecryption(
                                    isDecryptionResolved = false,
                                    senderUserId = event.senderUserId
                                )
                            )
                        }
                    }

                    is MLSMessageFailureResolution.OutOfSync -> {
                        eventLogger.logFailure(it, "protocol" to "MLS", "mlsOutcome" to "OUT_OF_SYNC")
                        staleEpochVerifier.verifyEpoch(
                            transactionContext,
                            event.conversationId,
                            event.subconversationId,
                            event.messageInstant
                        )
                    }

                    MLSMessageFailureResolution.ResetConversation -> {
                        eventLogger.logFailure(it, "protocol" to "MLS", "mlsOutcome" to "OUT_OF_SYNC")
                        resetMLSConversation(event.conversationId, transactionContext)
                    }
                }
            }.onSuccess { batchResult ->
                batchResult.forEach { message ->
                    if (message is MessageUnpackResult.ApplicationMessage) {
                        processApplicationMessage(transactionContext, message, deliveryInfo)
                    }
                    eventLogger.logSuccess(
                        "protocol" to "MLS",
                        "isPartOfMLSBatch" to (batchResult.size > 1),
                        "messageType" to message.messageTypeDescription
                    )
                    // reset the time for next messages, if this is a batch
                    eventLogger = logger.createEventProcessingLogger(event)
                }
            }
    }

    private suspend fun processApplicationMessage(
        transactionContext: CryptoTransactionContext,
        it: MessageUnpackResult.ApplicationMessage,
        deliveryInfo: EventDeliveryInfo
    ) {
        if (it.content.legalHoldStatus != Conversation.LegalHoldStatus.UNKNOWN) {
            legalHoldHandler.handleNewMessage(it, isLive = deliveryInfo.source == EventSource.LIVE)
        }
        handleSuccessfulResult(transactionContext, it)
        onMessageInserted(it)
    }

    private val MessageUnpackResult.messageTypeDescription
        get() = if (this is MessageUnpackResult.ApplicationMessage) {
            content.messageContent.typeDescription()
        } else {
            "Handshake"
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

    private suspend fun handleSuccessfulResult(
        transactionContext: CryptoTransactionContext,
        result: MessageUnpackResult.ApplicationMessage
    ) {
        applicationMessageHandler.handleContent(
            transactionContext = transactionContext,
            conversationId = result.conversationId,
            messageInstant = result.instant,
            senderUserId = result.senderUserId,
            senderClientId = result.senderClientId,
            content = result.content
        )
    }

    override suspend fun flushPendingSideEffects() {
        applicationMessageHandler.flushPendingSideEffects()
    }
}
