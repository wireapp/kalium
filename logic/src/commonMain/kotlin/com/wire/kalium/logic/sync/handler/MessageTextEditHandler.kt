package com.wire.kalium.logic.sync.handler

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.flatMap

class MessageTextEditHandler(private val messageRepository: MessageRepository) {

    suspend fun handle(
        message: Message,
        messageContent: MessageContent.TextEdited
    ) = messageRepository.updateTextMessageContent(
        conversationId = message.conversationId,
        messageId = messageContent.editMessageId,
        newTextContent = messageContent.newContent
    ).flatMap {
        messageRepository.markMessageAsEdited(
            messageUuid = messageContent.editMessageId,
            conversationId = message.conversationId,
            timeStamp = message.date
        )
    }.flatMap {
        // whenever "other" client updates the content message, it discards the previous id
        // and replaces it by a new id
        // in order to still point to the same message on our device and every other device displaying the message,
        // the "other client" sends us the "old" id, which will be equal to the
        // one we currently have, and a "new" id. we, first adjust the changes to the status and content
        // using the old id and after that we update the id which the one provided by the "other" client
        // when the update happens again we repeat the process, by doing it we always reference to the id
        // that the "other" client references, so that we can talk about the same message reference
        messageRepository.updateMessageId(
            conversationId = message.conversationId,
            oldMessageId = messageContent.editMessageId,
            newMessageId = message.id
        )
    }

}


