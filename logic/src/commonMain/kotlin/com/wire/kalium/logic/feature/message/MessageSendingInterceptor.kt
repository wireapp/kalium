package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.util.MessageContentEncoder

interface MessageSendingInterceptor {
    suspend fun prepareMessage(message: Message.Regular): Either<CoreFailure, Message.Regular>
}

class MessageSendingInterceptorImpl(
    private val messageContentEncoder: MessageContentEncoder,
    private val messageRepository: MessageRepository
) : MessageSendingInterceptor {

    override suspend fun prepareMessage(message: Message.Regular): Either<CoreFailure, Message.Regular> {
        val replyMessageContent = message.content

        if (replyMessageContent !is MessageContent.Text
            || replyMessageContent.quotedMessageReference == null
        ) {
            return Either.Right(message)
        }

        return messageRepository.getMessageById(message.conversationId, replyMessageContent.quotedMessageReference.quotedMessageId)
            .map { originalMessage ->
                val encodedMessageContent = messageContentEncoder.encodeMessageContent(
                    messageDate = originalMessage.date,
                    messageContent = originalMessage.content
                )

                message.copy(
                    content = replyMessageContent.copy(
                        quotedMessageReference = replyMessageContent.quotedMessageReference.copy(
                            quotedMessageSha256 = encodedMessageContent?.sha256Digest
                        )
                    )
                )
            }
    }
}
