package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder


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
    val receiptMode: ReceiptMode?,
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

@Serializable(with = ReceiptMode.ReceiptModeAsIntSerializer::class)
enum class ReceiptMode(val value: Int) {
    DISABLED(0),
    ENABLED(1);

    object ReceiptModeAsIntSerializer : KSerializer<ReceiptMode> {
        override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ReceiptMode", PrimitiveKind.INT)
        override fun serialize(encoder: Encoder, value: ReceiptMode) {
            encoder.encodeInt(value.value)
        }

        override fun deserialize(decoder: Decoder): ReceiptMode {
            val value = decoder.decodeInt()
            return if (value > 0) ENABLED else DISABLED
        }
    }
}

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
