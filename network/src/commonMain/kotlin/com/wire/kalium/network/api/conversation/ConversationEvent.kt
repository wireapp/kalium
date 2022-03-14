package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMember(
    @SerialName("conversation_role") val conversationRole: String?, // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("qualified_id") val qualifiedId: UserId
)

@Serializable
data class ConversationMembers(
    @SerialName("user_ids") val userIds: List<String>, // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("users") val users: List<ConversationMember>
)

@Serializable
data class ConversationUsers(
    @SerialName("user_ids") val userIds: List<String>, // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("qualified_user_ids") val qualifiedUserIds: List<UserId>
)
