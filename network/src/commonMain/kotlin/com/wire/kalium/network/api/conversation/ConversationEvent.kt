package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ConversationMembers(
    @SerialName("user_ids") val userIds: List<String>,
    @SerialName("users") val users: List<ConversationMemberDTO.Other>
)

@Serializable
data class ConversationUsers(
    @SerialName("user_ids") val userIds: List<String>,
    @SerialName("qualified_user_ids") val qualifiedUserIds: List<UserId>
)
