package com.wire.kalium.logic.data.event

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.user.UserId

sealed class Event(open val id: String) {

    sealed class Conversation(
        id: String,
        open val conversationId: ConversationId
    ) : Event(id) {
        data class NewMessage(
            override val id: String, override val conversationId: ConversationId,
            val senderUserId: UserId,
            val senderClientId: ClientId,
            val time: String,
            val content: String
        ) : Conversation(id, conversationId)
    }

    data class Unknown(override val id: String): Event(id)
}
