package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.ConversationAccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable


@Serializable
data class CreateConversationRequest(
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>,
    // the name is optional in swagger but this should not be the case
    // since there is endpoint for one2one and self conversations
    @SerialName("name")
    val name: String,
    @SerialName("access")
    val access: List<ConversationAccess>,
    @SerialName("access_role")
    val conversationAccess: ConversationAccess,
    @SerialName("team")
    val team: Team,
    @SerialName("message_timer")
    val messageTimer: Int?, // Per-conversation message time
    @SerialName("receipt_mode")
    val receiptMode: Int,
    @SerialName("conversation_role")
    val conversationRole: String, // Role name, between 2 and 128 chars, 'wire_' prefix is reserved
                                    // for roles designed by Wire (i.e., no custom roles can have the same prefix)
)

@Serializable
data class Team(
    @SerialName("managed")
    val managed: Boolean,
    @SerialName("teamid")
    val teamid: String
)
