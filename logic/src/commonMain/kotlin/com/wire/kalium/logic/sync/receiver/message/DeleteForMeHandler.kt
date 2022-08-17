package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.kaliumLogger

class DeleteForMeHandler(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository,
    private val userRepository: UserRepository
) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.DeleteForMe
    ){
        // The conversationId comes with the hidden message[content] only carries the conversationId VALUE,
        // we need to get the DOMAIN from the self conversationId[here is the message.conversationId]
        val conversationId = messageContent.conversationId ?:
        ConversationId(messageContent.unqualifiedConversationId, userRepository.getSelfUserId().domain)

        if (message.conversationId == conversationRepository.getSelfConversationId())
            messageRepository.deleteMessage(
                messageUuid = messageContent.messageId,
                conversationId = conversationId
            )
        else kaliumLogger.withFeatureId(KaliumLogger.Companion.ApplicationFlow.EVENT_RECEIVER).i(message = "Delete message sender is not verified: $message")
    }

}
