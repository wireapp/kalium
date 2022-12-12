package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.IsMessageSentInSelfConversationUseCase
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.kaliumLogger

interface DeleteForMeHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DeleteForMe
    )
}

internal class DeleteForMeHandlerImpl internal constructor(
    private val messageRepository: MessageRepository,
    private val isMessageSentInSelfConversation: IsMessageSentInSelfConversationUseCase
) : DeleteForMeHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.DeleteForMe
    ) {
        if (isMessageSentInSelfConversation(message)) {
            messageRepository.deleteMessage(
                messageUuid = messageContent.messageId,
                conversationId = messageContent.conversationId
            )
        } else {
            kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER)
                .i(message = "Delete message sender is not verified: $messageContent")
        }
    }

}
