package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateConversationRequest(
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>?,
    @SerialName("name")
    val name: String?,
    @SerialName("access")
    val access: List<ConversationAccessDTO>?,
    @SerialName("access_role_v2")
    val accessRole: List<ConversationAccessRoleDTO>?,
    @SerialName("team")
    val convTeamInfo: ConvTeamInfo?,
    @SerialName("message_timer")
    val messageTimer: Long?, // Per-conversation message time
    // Receipt mode, controls if read receipts are enabled for the conversation.
    // Any positive value is interpreted as enabled.
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles
    // designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role")
    val conversationRole: String?,
    @SerialName("protocol")
    val protocol: ConvProtocol?,
    // Only needed for MLS conversations
    @SerialName("creator_client")
    val creatorClient: String?
)

@Serializable
internal data class CreateConversationRequestV3(
    @SerialName("qualified_users")
    val qualifiedUsers: List<UserId>?,
    @SerialName("name")
    val name: String?,
    @SerialName("access")
    val access: List<ConversationAccessDTO>?,
    @SerialName("access_role")
    val accessRole: List<ConversationAccessRoleDTO>?,
    @SerialName("team")
    val convTeamInfo: ConvTeamInfo?,
    @SerialName("message_timer")
    val messageTimer: Long?, // Per-conversation message time
    // Receipt mode, controls if read receipts are enabled for the conversation.
    // Any positive value is interpreted as enabled.
    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles
    // designed by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role")
    val conversationRole: String?,
    @SerialName("protocol")
    val protocol: ConvProtocol?,
    // Only needed for MLS conversations
    @SerialName("creator_client")
    val creatorClient: String?
)

@Serializable
enum class ConvProtocol {
    @SerialName("proteus")
    PROTEUS,

    @SerialName("mls")
    MLS;

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
