package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddConversationMembersRequest (
    @SerialName("qualified_users")
    val users: List<UserId>,
    @SerialName("conversation_role")
    val conversationRole: String?
)

@Serializable
sealed class ConversationMemberChangeDTO {
    // TODO: the server response with an event aka, UserAdded model is inaccurate
    object Unchanged: ConversationMemberChangeDTO()

    @Serializable
    data class Changed(
        @SerialName("type")
        val eventType: String,
        @SerialName("qualified_conversation")
        val qualifiedConversationId: ConversationId,
        @SerialName("qualified_from")
        val fromUser: UserId,
        @SerialName("time")
        val time: String
    ): ConversationMemberChangeDTO()

}
