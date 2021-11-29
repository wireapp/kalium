package com.wire.kalium.api.conversation

import com.wire.kalium.models.backend.AccessRole
import com.wire.kalium.models.backend.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CreateConversationRequest(
    @SerialName("access")
    val access: List<AccessRole>,
    @SerialName("access_role")
    val accessRole: String, // How users can join conversations ['private', 'invite', 'link', 'code']
    @SerialName("conversation_role")
    val conversationRole: String,
    @SerialName("message_timer")
    val messageTimer: Int,
    @SerialName("name")
    val name: String,
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>,
    @SerialName("receipt_mode")
    val receiptMode: Int,
    @SerialName("team")
    val team: Team
)

@Serializable
data class Team(
    @SerialName("managed")
    val managed: Boolean,
    @SerialName("teamid")
    val teamid: String
)
