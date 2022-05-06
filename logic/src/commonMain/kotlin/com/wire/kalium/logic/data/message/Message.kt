package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId

data class Message(
    val id: String,
    val content: MessageContent,
    val conversationId: ConversationId,
    val date: String,
    val senderUserId: UserId,
    val senderClientId: ClientId,
    val status: Status,
    val visibility: Visibility = Visibility.VISIBLE
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}
