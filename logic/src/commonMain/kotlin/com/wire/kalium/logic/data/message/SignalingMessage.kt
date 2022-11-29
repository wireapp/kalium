package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class SignalingMessage(
    val id: String,
    val content: MessageContent.Signaling,
    val conversationId: ConversationId,
    val date: String,
    val senderUserId: UserId,
    val senderClientId: ClientId
)
