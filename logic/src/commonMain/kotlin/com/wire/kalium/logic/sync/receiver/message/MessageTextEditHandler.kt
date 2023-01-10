package com.wire.kalium.logic.sync.receiver.message

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger

interface MessageTextEditHandler {
    suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ): Either<CoreFailure, Unit>
}

class MessageTextEditHandlerImpl(
    private val messageRepository: MessageRepository
) : MessageTextEditHandler {

    override suspend fun handle(
        message: Message.Signaling,
        messageContent: MessageContent.TextEdited
    ) = messageRepository.getMessageById(message.conversationId, messageContent.editMessageId).flatMap { currentMessage ->

        if (currentMessage.senderUserId != message.senderUserId) {
            val obfuscatedId = message.senderUserId.toString().obfuscateId()
            kaliumLogger.w(
                message = "User '$obfuscatedId' attempted to edit a message from another user. Ignoring the edit completely"
            )
            // Same as message not found. _i.e._ not found for the original sender at least
            return@flatMap Either.Left(StorageFailure.DataNotFound)
        }

        messageRepository.updateTextMessage(
            conversationId = message.conversationId,
            messageContent = messageContent,
            newMessageId = message.id,
            editTimeStamp = message.date
        )
    }

}
