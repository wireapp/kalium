package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import java.util.UUID

suspend fun CallManager.senCallingMessage(conversationId: ConversationId, data: String) {
    val messageContent =  MessageContent.Calling(data)
    val message =  Message(UUID.randomUUID().toString(), messageContent, conversationId)
    messageSender.getRecipientsAndAttemptSend(conversationId, message)
}
