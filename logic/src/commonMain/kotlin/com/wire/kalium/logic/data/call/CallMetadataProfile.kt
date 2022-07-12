package com.wire.kalium.logic.data.call

import com.wire.kalium.logic.data.conversation.Conversation

data class CallMetadataProfile(
    val data: Map<String, CallMetadata>
) {
    operator fun get(conversationId: String): CallMetadata? = data[conversationId]
}

data class CallMetadata(
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
