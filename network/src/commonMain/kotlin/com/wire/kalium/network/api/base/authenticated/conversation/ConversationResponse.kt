package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlobalTeamConversationResponse(
    @SerialName("creator")
    val creator: String?,

    @SerialName("name")
    val name: String?,

    @SerialName("qualified_id")
    val id: ConversationId,

    @SerialName("group_id")
    val groupId: String?,

    @SerialName("epoch")
    val epoch: ULong?,

    @SerialName("team")
    val teamId: TeamId?,

    @SerialName("cipher_suite")
    val mlsCipherSuiteTag: Int?,

    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode?,

    @SerialName("access")
    val access: Set<ConversationAccessDTO>
)

@Serializable
data class ConversationResponse(
    @SerialName("creator")
    val creator: String,

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
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL,

    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode?,
) {

    val isOneOnOneConversation: Boolean
        get() = type in setOf(
            Type.ONE_TO_ONE,
            Type.WAIT_FOR_CONNECTION
        )

    @Suppress("MagicNumber")
    enum class Type(val id: Int) {
        GROUP(0), SELF(1), ONE_TO_ONE(2), WAIT_FOR_CONNECTION(3), GLOBAL_TEAM(4);

        companion object {
            fun fromId(id: Int): Type = values().first { type -> type.id == id }
        }
    }
}

@Serializable
data class ConversationResponseV3(
    @SerialName("creator")
    val creator: String,

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

    @SerialName("access_role_v2")
    val accessRole: Set<ConversationAccessRoleDTO> = ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL,

    @SerialName("receipt_mode")
    val receiptMode: ReceiptMode?,
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
