package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId

enum class CallStatus {
    INCOMING,
    MISSED,
    ANSWERED,
    ESTABLISHED,
    CLOSED
}

data class Call(
    val conversationId: ConversationId,
    val status: CallStatus
)
