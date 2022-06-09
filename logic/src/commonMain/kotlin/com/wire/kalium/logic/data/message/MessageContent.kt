package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.Member
import com.wire.kalium.protobuf.messages.QualifiedConversationId

sealed class MessageContent {

    sealed class Client : MessageContent()
    sealed class Server : MessageContent()

    // client message content types
    data class Text(val value: String) : Client()
    data class Calling(val value: String) : Client()
    data class Asset(val value: AssetContent) : Client()
    data class DeleteMessage(val messageId: String) : Client()
    data class TextEdited(val editMessageId :String, val newContent : String) : Client()
    data class DeleteForMe(
        val messageId: String,
        val conversationId: String,
        val qualifiedConversationId: QualifiedConversationId?
    ) : Client()

    data class Unknown(val encodedData: ByteArray? = null) : Client() // messages that aren't yet handled properly but stored in db in case
    object Ignored : Client() // messages that aren't processed in any way
    object Empty : Client()

    // server message content types
    sealed class MemberChange(open val members: List<Member>) : Server() {
        data class Added(override val members: List<Member>) : MemberChange(members)
        data class Removed(override val members: List<Member>) : MemberChange(members)
    }
}
