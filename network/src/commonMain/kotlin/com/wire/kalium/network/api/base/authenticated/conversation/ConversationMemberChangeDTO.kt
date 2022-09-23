package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AddConversationMembersRequest(
    @SerialName("qualified_users")
    val users: List<UserId>,
    @SerialName("conversation_role")
    val conversationRole: String?
)

@Serializable
sealed class ConversationMemberAddedDTO {
    // TODO: the server response with an event aka, UserAdded model is inaccurate
    object Unchanged : ConversationMemberAddedDTO()

    @Serializable
    @SerialName("conversation.member-join")
    data class Changed(
        @SerialName("type")
        val eventType: String,
        @SerialName("qualified_conversation")
        val qualifiedConversationId: ConversationId,
        @SerialName("qualified_from")
        val fromUser: UserId,
        @SerialName("time")
        val time: String
    ) : ConversationMemberAddedDTO()
}

@Serializable
sealed class ConversationMemberRemovedDTO {
    // TODO: the server response with an event aka, UserAdded model is inaccurate
    object Unchanged : ConversationMemberRemovedDTO()

    @Serializable
    @SerialName("conversation.member-leave")
    data class Changed(
        @SerialName("type")
        val eventType: String,
        @SerialName("qualified_conversation")
        val qualifiedConversationId: ConversationId,
        @SerialName("qualified_from")
        val fromUser: UserId,
        @SerialName("time")
        val time: String
    ) : ConversationMemberRemovedDTO()

}
