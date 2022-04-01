package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class Message(
    val id: String,
    val content: MessageContent,
    val conversationId: ConversationId,
    val date: String = "",
    val senderUserId: UserId = UserId("", ""),
    val senderClientId: ClientId = ClientId(""),
    val status: Status? = Status.SENT
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }
}
