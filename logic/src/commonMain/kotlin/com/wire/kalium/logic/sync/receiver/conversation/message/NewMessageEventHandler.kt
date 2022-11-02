package com.wire.kalium.logic.sync.receiver.conversation.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger

internal interface NewMessageEventHandler {
    suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage)
    suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage)
}

internal class NewMessageEventHandlerImpl(
    private val proteusMessageUnpacker: ProteusMessageUnpacker,
    private val mlsMessageUnpacker: MLSMessageUnpacker,
    private val applicationMessageHandler: ApplicationMessageHandler
) : NewMessageEventHandler {

    private val logger by lazy { kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER) }

    override suspend fun handleNewProteusMessage(event: Event.Conversation.NewMessage) {
        proteusMessageUnpacker.unpackProteusMessage(event)
            .onFailure {
                applicationMessageHandler.handleDecryptionError(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = event.senderClientId,
                    content = MessageContent.FailedDecryption(event.encryptedExternalContent?.data)
                )
            }.onSuccess {
                handleSuccessfulResult(it)
            }
    }

    override suspend fun handleNewMLSMessage(event: Event.Conversation.NewMLSMessage) {
        mlsMessageUnpacker.unpackMlsMessage(event)
            .onFailure {
                applicationMessageHandler.handleDecryptionError(
                    eventId = event.id,
                    conversationId = event.conversationId,
                    timestampIso = event.timestampIso,
                    senderUserId = event.senderUserId,
                    senderClientId = ClientId(""), // TODO(mls): client ID not available for MLS messages
                    content = MessageContent.FailedDecryption()
                )
            }.onSuccess {
                handleSuccessfulResult(it)
            }
    }

    private suspend fun handleSuccessfulResult(result: MessageUnpackResult) {
        if (result is MessageUnpackResult.ApplicationMessage) {
            applicationMessageHandler.handleContent(
                conversationId = result.conversationId,
                timestampIso = result.timestampIso,
                senderUserId = result.senderUserId,
                senderClientId = result.senderClientId,
                content = result.content
            )
        } else {
            // NO-OP. Pure Protocol messages are handled by the unpackers
        }
    }
}
