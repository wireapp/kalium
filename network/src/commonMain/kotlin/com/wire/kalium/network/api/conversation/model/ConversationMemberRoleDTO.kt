package com.wire.kalium.network.api.conversation.model

import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.notification.EventContentDTO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMemberRoleDTO(
    @SerialName("conversation_role") val conversationRole: String
)
