/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.network.api.authenticated.conversation

import com.wire.kalium.network.api.authenticated.serverpublickey.MLSPublicKeysDTO
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.SubconversationId
import com.wire.kalium.network.api.model.TeamId
import com.wire.kalium.network.api.model.UserId
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
data class ConversationResponse(
    @SerialName("creator")
    val creator: String?,

    @SerialName("members")
    val members: ConversationMembersResponse,

    @SerialName("name")
    val name: String?,

    @SerialName("qualified_id")
    val id: ConversationId,

    @SerialName("group_id")
    val groupId: String?,

    @SerialName("epoch")
    val epoch: ULong?,

    @Deprecated(
        "For team 1on1 it can be a false group type",
        ReplaceWith(
            "this.toConversationType(selfUserTeamId)",
            "com.wire.kalium.logic.data.conversation.toConversationType",
        )
    )
    @Serializable(with = ConversationTypeSerializer::class)
    val type: Type,

    @SerialName("message_timer")
    val messageTimer: Long?,

    @SerialName("team")
    val teamId: TeamId?,

    @SerialName("protocol")
    val protocol: ConvProtocol,

    @SerialName("last_event_time")
    val lastEventTime: String,

    @SerialName("cipher_suite")
    val mlsCipherSuiteTag: Int?,

    @SerialName("access")
    val access: Set<ConversationAccessDTO>,

    @SerialName("access_role_v2")
    val accessRole: Set<ConversationAccessRoleDTO>?,

    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,

    @SerialName("public_keys")
    val publicKeys: MLSPublicKeysDTO? = null,

    /**
     * Only groups are expected to have a non-null value.
     * Since API V8
     * @see GroupType
     */
    @SerialName("group_conv_type")
    val conversationGroupType: GroupType? = null,

    @SerialName("add_permission")
    val channelAddUserPermissionTypeDTO: ChannelAddPermissionTypeDTO? = null,
) {

    @Suppress("MagicNumber")
    enum class Type(val id: Int) {
        GROUP(0),
        SELF(1),
        ONE_TO_ONE(2),

        @Deprecated("It's planned to be removed after v4", replaceWith = ReplaceWith("ONE_TO_ONE"))
        WAIT_FOR_CONNECTION(3);

        companion object {
            fun fromId(id: Int): Type = values().first { type -> type.id == id }
        }
    }

    enum class GroupType {
        @SerialName("group_conversation")
        REGULAR_GROUP,

        @SerialName("channel")
        CHANNEL,
    }

    fun toV6(): ConversationResponseV6 =
        ConversationResponseV6(this, publicKeys ?: MLSPublicKeysDTO(null))
}

@Serializable
data class ConversationResponseV3(
    @SerialName("creator")
    val creator: String?,

    @SerialName("members")
    val members: ConversationMembersResponse,

    @SerialName("name")
    val name: String?,

    @SerialName("qualified_id")
    val id: ConversationId,

    @SerialName("group_id")
    val groupId: String?,

    @SerialName("epoch")
    val epoch: ULong?,

    @Serializable(with = ConversationTypeSerializer::class)
    val type: ConversationResponse.Type,

    @SerialName("message_timer")
    val messageTimer: Long?,

    @SerialName("team")
    val teamId: TeamId?,

    @SerialName("protocol")
    val protocol: ConvProtocol,

    @SerialName("last_event_time")
    val lastEventTime: String,

    @SerialName("cipher_suite")
    val mlsCipherSuiteTag: Int?,

    @SerialName("access")
    val access: Set<ConversationAccessDTO>,

    @SerialName("access_role")
    val accessRole: Set<ConversationAccessRoleDTO>?,

    @SerialName("access_role_v2")
    val accessRoleV2: Set<ConversationAccessRoleDTO>?,

    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode,
)

@Serializable
data class ConversationResponseV6(
    @SerialName("conversation")
    val conversation: ConversationResponse,
    @SerialName("public_keys")
    val publicKeys: MLSPublicKeysDTO
)

@Serializable
data class ConversationMembersResponse(
    @SerialName("self")
    val self: ConversationMemberDTO.Self,

    @SerialName("others")
    val otherMembers: List<ConversationMemberDTO.Other>
)

sealed class ConversationMemberDTO {
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed
    // by Wire (i.e., no custom roles can have the same prefix)
    // in swagger conversation_role is an optional field but according to Akshay:
    // Hmm, the field is optional when sending it to the server. The server will always send the field.
    // (The server assumes admin when the field is missing, I don't have the context behind this decision)
    abstract val conversationRole: String
    abstract val id: UserId
    abstract val service: ServiceReferenceDTO?

    @Serializable
    data class Self(
        @SerialName(ID_SERIAL_NAME) override val id: UserId,
        @SerialName(CONV_ROLE_SERIAL_NAME) override val conversationRole: String,
        @SerialName(SERVICE_SERIAL_NAME) override val service: ServiceReferenceDTO? = null,
        @SerialName("hidden") val hidden: Boolean? = null,
        @SerialName("hidden_ref") val hiddenRef: String? = null,
        @SerialName("otr_archived") val otrArchived: Boolean? = null,
        @SerialName("otr_archived_ref") val otrArchivedRef: String? = null,
        @SerialName("otr_muted_ref") val otrMutedRef: String? = null,
        @SerialName("otr_muted_status") @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus? = null
    ) : ConversationMemberDTO()

    @Serializable
    data class Other(
        @SerialName(ID_SERIAL_NAME) override val id: UserId,
        @SerialName(CONV_ROLE_SERIAL_NAME) override val conversationRole: String,
        @SerialName(SERVICE_SERIAL_NAME) override val service: ServiceReferenceDTO? = null
    ) : ConversationMemberDTO()

    private companion object {
        const val ID_SERIAL_NAME = "qualified_id"
        const val CONV_ROLE_SERIAL_NAME = "conversation_role"
        const val SERVICE_SERIAL_NAME = "service"
    }
}

@Serializable
data class ServiceReferenceDTO(
    @SerialName("id")
    val id: String,

    @SerialName("provider")
    val provider: String
)

@Serializable
data class AddServiceRequest(
    @SerialName("service")
    val id: String,

    @SerialName("provider")
    val provider: String
)

@Serializable
data class SubconversationResponse(
    @SerialName("subconv_id")
    val id: SubconversationId,

    @SerialName("parent_qualified_id")
    val parentId: ConversationId,

    @SerialName("group_id")
    val groupId: String,

    @SerialName("epoch")
    val epoch: ULong,

    @SerialName("epoch_timestamp")
    val epochTimestamp: String?,

    @SerialName("cipher_suite")
    val mlsCipherSuiteTag: Int?,

    @SerialName("members")
    val members: List<SubconversationMemberDTO>,
)

@Serializable
data class SubconversationMemberDTO(
    @SerialName("client_id") val clientId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("domain") val domain: String
)

@OptIn(ExperimentalSerializationApi::class)
@Serializer(ConversationResponse.Type::class)
class ConversationTypeSerializer : KSerializer<ConversationResponse.Type> {
    override val descriptor = PrimitiveSerialDescriptor("type", PrimitiveKind.INT)

    override fun serialize(encoder: Encoder, value: ConversationResponse.Type) = encoder.encodeInt(value.id)

    override fun deserialize(decoder: Decoder): ConversationResponse.Type {
        val rawValue = decoder.decodeInt()
        return ConversationResponse.Type.fromId(rawValue)
    }
}
