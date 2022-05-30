package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.protobuf.messages.QualifiedConversationId

sealed class MessageContent {
    data class Text(val value: String) : MessageContent()
    data class Calling(val value: String) : MessageContent()
    data class Asset(val value: AssetContent) : MessageContent()
    data class DeleteMessage(val messageId: String) : MessageContent()
    data class DeleteForMe(
        val messageId: String,
        val conversationId: String,
        val qualifiedConversationId: QualifiedConversationId?
    ) : MessageContent()
    sealed class MemberChange(open val members: List<Member>) : MessageContent() {
        data class Join(override val members: List<Member>): MemberChange(members)
        data class Leave(override val members: List<Member>): MemberChange(members)
    }
    object Unknown : MessageContent()
}
