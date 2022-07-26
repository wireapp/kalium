package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddParticipantRequest (
    @SerialName("qualified_users")
    val users: List<UserId>,
    @SerialName("conversation_role")
    val conversationRole: String?
)


sealed class AddParticipantResponse {
    // TODO: the server response with an event aka, UserAdded model is inaccurate
    object ConversationUnchanged: AddParticipantResponse()

    @Serializable
    data class UserAdded(
        @SerialName("type")
        val eventType: String,
        @SerialName("qualified_conversation")
        val qualifiedConversationId: ConversationId,
        @SerialName("qualified_from")
        val fromUser: UserId,
        @SerialName("time")
        val time: String
    ): AddParticipantResponse()
}
