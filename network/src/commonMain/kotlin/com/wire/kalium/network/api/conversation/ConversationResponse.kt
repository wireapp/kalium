package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.TeamId
import com.wire.kalium.network.api.UserId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

    @Serializable(with = ConversationTypeSerializer::class)
    val type: Type,

    @SerialName("message_timer")
    val messageTimer: Int?,

    @SerialName("team")
    val teamId: TeamId?,

    @SerialName("protocol")
    val protocol: ConvProtocol,

    @SerialName("last_event_time")
    val lastEventTime: String
) {

    val isOneOnOneConversation: Boolean
        get() = type in setOf(
            Type.ONE_TO_ONE,
            Type.WAIT_FOR_CONNECTION,
            Type.INCOMING_CONNECTION
        )

    enum class Type(val id: Int) {
        GROUP(0), SELF(1), ONE_TO_ONE(2), WAIT_FOR_CONNECTION(3), INCOMING_CONNECTION(4);

        companion object {
            fun fromId(id: Int): Type = values().first { type -> type.id == id }
        }
    }
}

@Serializable
data class ConversationMembersResponse(
    @SerialName("self")
    val self: ConversationSelfMemberResponse,

    @SerialName("others")
    val otherMembers: List<ConversationOtherMembersResponse>
)

@Serializable
data class ConversationSelfMemberResponse(
    @SerialName("qualified_id") override val userId: UserId,
    @SerialName("otr_muted_ref") val otrMutedRef: String? = null,
    @SerialName("otr_muted_status") @Serializable(with = MutedStatusSerializer::class) val otrMutedStatus: MutedStatus? = null
    /*
    // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed
    // by Wire (i.e., no custom roles can have the same prefix)
    @SerialName("conversation_role") val conversationRole: String? = null,

    @SerialName("service") val service: ServiceReferenceResponse? = null,

    //@SerialName("status") val status
    //@SerialName("status_ref") val status
    //@SerialName("status_time") val status

    @SerialName("otr_muted_ref") val otrMutedReference: String? = null,
    @SerialName("otr_muted_status") val otrMutedStatus: Int? = null,

    @SerialName("hidden") val hidden: Boolean? = null,
    @SerialName("hidden_ref") val hiddenReference: String? = null,

    @SerialName("otr_archived") val otrArchived: Boolean? = null,
    @SerialName("otr_archived_ref") val otrArchivedReference: String? = null,
    */
) : ConversationMemberResponse

@Serializable
data class ConversationOtherMembersResponse(
    @SerialName("service")
    val service: ServiceReferenceResponse? = null,

    @SerialName("qualified_id")
    override val userId: UserId,
) : ConversationMemberResponse

interface ConversationMemberResponse {
    val userId: UserId
}

@Serializable
data class ServiceReferenceResponse(
    @SerialName("id")
    val id: String,

    @SerialName("provider")
    val provider: String
)
