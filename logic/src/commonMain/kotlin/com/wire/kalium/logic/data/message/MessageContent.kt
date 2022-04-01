package com.wire.kalium.logic.data.message
import com.wire.kalium.logic.data.conversation.ConversationId

sealed class MessageContent {

    data class Text(val value: String) : MessageContent()
    data class Calling(val value: String) : MessageContent()
    data class Asset(val value: AssetContent) : MessageContent()
    data class DeleteMessage(val messageId: String) : MessageContent()
    data class DeleteForMe(val messageId: String, val conversationId: ConversationId) : MessageContent()
    object Unknown : MessageContent()
}
