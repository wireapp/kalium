package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.feature.call.Call

data class CallProfile(
    val calls: Map<String, Call>
) {
    operator fun get(conversationId: String): Call? = calls[conversationId]
}

data class CallMetaDataProfile(
    val data: Map<String, CallMetaData>
)

data class CallMetaData(
    val isMuted: Boolean,
    val isCameraOn: Boolean,
    val conversationName: String?,
    val conversationType: Conversation.Type,
    val callerName: String?,
    val callerTeamName: String?,
    val establishedTime: String? = null,
    val participants: List<Participant> = emptyList(),
    val maxParticipants: Int = 0 // Was used for tracking
)
