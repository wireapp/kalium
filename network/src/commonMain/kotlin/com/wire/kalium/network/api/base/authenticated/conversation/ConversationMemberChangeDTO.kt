package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
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

    data class Changed(val event: EventContentDTO.Conversation.MemberJoinDTO) : ConversationMemberAddedDTO()
}

@Serializable
sealed class ConversationMemberRemovedDTO {
    // TODO: the server response with an event aka, UserAdded model is inaccurate
    object Unchanged : ConversationMemberRemovedDTO()

    data class Changed(val event: EventContentDTO.Conversation.MemberLeaveDTO) : ConversationMemberRemovedDTO()

}
