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

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.ProteusFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventLoggingStatus
import com.wire.kalium.logic.data.event.logEventProcessing
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.serialization.toJsonElement

internal interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage)
    suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage)
}

internal class NewMessageEventHandlerImpl(
    private val proteusMessageUnpacker: ProteusMessageUnpacker,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val enqueueSelfDeletion: (conversationId: ConversationId, messageId: String) -> Unit,
    private val selfUserId: UserId
) : NewMessageEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage) {
        proteusMessageUnpacker.unpackProteusMessage(event)
            .onFailure {
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

                applicationMessageHandler.handleDecryptionError(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    content = MessageContent.FailedDecryption(
                        encodedData = event.encryptedExternalContent?.data,
                        isDecryptionResolved = false,
                        senderUserId = event.senderUserId,
                        clientId = ClientId(event.senderClientId.value)
                    )
                )
            }.onSuccess {
                if (it is MessageUnpackResult.ApplicationMessage) {
                    handleSuccessfulResult(it)
                    onMessageInserted(it)
                }
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
    }

    override suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) {
        mlsMessageUnpacker.unpackMlsMessage(event)
            .onFailure {

                val logMap = mapOf(
                    "event" to event.toLogMap(),
                    "errorInfo" to "$it",
                    "protocol" to "MLS"
                )

                logger.e("Failed to decrypt event: ${logMap.toJsonElement()}")

                applicationMessageHandler.handleDecryptionError(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                    content = MessageContent.FailedDecryption(
                        isDecryptionResolved = false,
                        senderUserId = event.senderUserId
                    )
                )
            }.onSuccess {
                if (it is MessageUnpackResult.ApplicationMessage) {
                    handleSuccessfulResult(it)
                    onMessageInserted(it)
                }
                kaliumLogger
                    .logEventProcessing(
                        EventLoggingStatus.SUCCESS,
                        event
                    )
            }
    }

    private fun onMessageInserted(result: MessageUnpackResult.ApplicationMessage) {
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
            timestampIso = result.timestampIso,
            senderUserId = result.senderUserId,
            senderClientId = result.senderClientId,
            content = result.content
        )
    }
}
