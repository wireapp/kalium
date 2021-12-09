package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.ConversationAccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddParticipantRequest (
    @SerialName("qualified_users")
    val users: List<UserId>,
    @SerialName("conversation_role")
    val conversationRole: String
)


sealed class AddParticipantResponse {
    object ConversationUnchanged: AddParticipantResponse()

    @Serializable
    data class UserAdded(
        @SerialName("type")
        val eventType: String,
        @SerialName("data")
        val data: EventData,
        @SerialName("qualified_conversation")
        val qualifiedConversationId: ConversationId,
        @SerialName("qualified_from")
        val fromUser: UserId,
        @SerialName("time")
        val time: String
    ): AddParticipantResponse()
}
