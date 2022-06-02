package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.team.Team

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
    val callerId: String,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val callerName: String?,
    val callerTeamName: String?,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
)
