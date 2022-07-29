package com.wire.kalium.network.api.conversation.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemberRoleDTO(
    @SerialName("conversation_role") val conversationRole: String
)
