package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.ConversationAccess
import com.wire.kalium.network.api.model.ConversationAccessRole
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
    @SerialName("access_role_v2")
    val accessRole: List<ConversationAccessRole>,
    @SerialName("team")
    val convTeamInfo: ConvTeamInfo?,
    @SerialName("message_timer")
    val messageTimer: Int?, // Per-conversation message time
    // Receipt mode, controls if read receipts are enabled for the conversation.
    // Any positive value is interpreted as enabled.
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles
    // designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role")
    val conversationRole: String,
    @SerialName("protocol")
    val protocol: ConvProtocol?
)


@Serializable
enum class ReceiptMode(val value: Int) {
    DISABLED(0),
    ENABLED(1);
}

@Serializable
enum class ConvProtocol {
    @SerialName("proteus") PROTEUS,
    @SerialName("mls") MLS;

    override fun toString(): String {
        return this.name.lowercase()
    }
}

@Serializable
data class ConvTeamInfo(
    @Deprecated("Not parsed any more")
    @SerialName("managed") val managed: Boolean,
    @SerialName("teamid") val teamId: TeamId
)
