package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.util.MessageContentEncoder
import com.wire.kalium.logic.util.toTimeInMillis

interface MessageSendingInterceptor {
    suspend fun prepareMessage(message: Message.Regular): Either<CoreFailure, Message.Regular>
}

class MessageSendingInterceptorImpl(
    private val messageContentEncoder: MessageContentEncoder,
    private val messageRepository: MessageRepository
) : MessageSendingInterceptor {

    override suspend fun prepareMessage(message: Message.Regular): Either<CoreFailure, Message.Regular> {
        val replyMessageContent = message.content

        if (replyMessageContent !is MessageContent.Text) {
            return Either.Right(message)
        }

        return messageRepository.getMessageById(message.conversationId, message.id).map { originalMessage ->
            val encodedMessageContent = when (val messageContent = originalMessage.content) {
                is MessageContent.Asset ->
                    messageContentEncoder.encodeMessageAsset(
                        messageTimeStampInMillis = originalMessage.date.toTimeInMillis(),
                        assetId = messageContent.value.remoteData.assetId
                    )

                is MessageContent.Text ->
                    messageContentEncoder.encodeMessageTextBody(
                        messageTimeStampInMillis = originalMessage.date.toTimeInMillis(),
                        messageTextBody = messageContent.value
                    )

                else -> null
            }

            message.copy(
                content = replyMessageContent.copy(
                    quotedMessageReference = replyMessageContent.quotedMessageReference?.copy(
                        quotedMessageSha256 = encodedMessageContent?.asSHA256
                    )
                )
            )
        }
    }
}
