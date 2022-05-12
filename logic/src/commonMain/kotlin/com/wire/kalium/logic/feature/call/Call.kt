package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.call.Participant
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.publicuser.model.OtherUser
import com.wire.kalium.logic.data.team.Team

enum class CallStatus {
    STARTED,
    INCOMING,
    MISSED,
    ANSWERED,
    ESTABLISHED,
    CLOSED
}

data class Call(
    val conversationId: ConversationId,
    val status: CallStatus,
    val callerId: String,
    val conversationDetails: ConversationDetails,
    val caller: OtherUser?,
    val callerTeam: Team?,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
) {

    data class Builder(
        var conversationId: ConversationId? = null,
        var status: CallStatus? = null,
        var callerId: String? = null,
        var conversationDetails: ConversationDetails? = null,
        var caller: OtherUser? = null,
        var callerTeam: Team? = null,
        var participants: List<Participant> = emptyList(),
        var maxParticipants: Int = 0 // Was used for tracking
    ) {
        fun conversationId(id: ConversationId) = apply { conversationId = id }
        fun status(status: CallStatus) = apply { this.status = status }
        fun callerId(id: String) = apply { callerId = id }
        fun conversationDetails(details: ConversationDetails) = apply { conversationDetails = details }
        fun caller(user: OtherUser?) = apply { caller = user }
        fun team(team: Team?) = apply { callerTeam = team }
        fun participants(participants: List<Participant>) = apply { this.participants = participants }
        fun maxParticipants(maxParticipants: Int) = apply { this.maxParticipants = maxParticipants }

        fun build(): Call = Call(
            conversationId!!,
            status!!,
            callerId!!,
            conversationDetails!!,
            caller,
            callerTeam,
            participants,
            maxParticipants,
        )
    }
}
