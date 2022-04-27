package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlinx.datetime.Clock
import java.util.UUID

suspend fun CallManagerImpl.sendCallingMessage(conversationId: ConversationId, userId: UserId, clientId: ClientId, data: String) {
    val messageContent = MessageContent.Calling(data)
    val date = Clock.System.now().toString()
    val message = Message(UUID.randomUUID().toString(), messageContent, conversationId, date, userId, clientId, Message.Status.SENT)
    messageSender.trySendingOutgoingMessage(conversationId, message)
}
