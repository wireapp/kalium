package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId

enum class CallStatus {
    STARTED,
    INCOMING,
    MISSED,
    ANSWERED,
    ESTABLISHED,
    STILL_ONGOING,
    CLOSED_INTERNALLY, // Call terminated on current device only
    CLOSED, // call terminated everywhere
    REJECTED
}

data class Call(
    val conversationId: ConversationId,
    val status: CallStatus,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val callerId: String,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val callerName: String?,
    val callerTeamName: String?,
    val establishedTime: String? = null,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
)
