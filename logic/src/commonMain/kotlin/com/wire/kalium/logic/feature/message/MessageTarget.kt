package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.Recipient

sealed class MessageTarget {
    class Client(val recipients: List<Recipient>): MessageTarget()
    object Conversation: MessageTarget()
}
