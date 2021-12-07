package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddParticipantRequest (
    @SerialName("qualified_users")
    val users: List<UserId>,
    @SerialName("conversation_role")
    val conversationRole: String
)
