package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.MessageSender
import com.wire.kalium.logic.functional.Either
import kotlinx.datetime.Clock
import java.util.UUID

//TODO create unit test for this one in a different PR
class SendCallingMessage(private val messageSender: MessageSender) {
    suspend fun sendMessage(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
        data: String
    ): Either<CoreFailure, Unit> {
        val messageContent = MessageContent.Calling(data)
        val date = Clock.System.now().toString()
        val message = Message(UUID.randomUUID().toString(), messageContent, conversationId, date, userId, clientId, Message.Status.SENT)
        return messageSender.trySendingOutgoingMessage(conversationId, message)
    }
}
