package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.id.ConversationId

enum class CallStatus {
    STARTED,
    INCOMING,
    MISSED,
    ANSWERED,
    ESTABLISHED
}

data class Call(
    val conversationId: ConversationId,
    val status: CallStatus,
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val callerId: String,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
)
