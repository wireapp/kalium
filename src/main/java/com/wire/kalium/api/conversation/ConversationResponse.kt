package com.wire.kalium.api.conversation

import com.wire.kalium.models.backend.ConversationId
import com.wire.kalium.models.backend.UserId
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

        @SerialName("type")
        val type: Int,

        @SerialName("message_timer")
        val messageTimer: Int
)

@Serializable
data class ConversationMembersResponse(
        @SerialName("self")
        val self: ConversationSelfMemberResponse,

        @SerialName("others")
        val otherMembers: List<ConversationOtherMembersResponse>
)

@Serializable
data class ConversationSelfMemberResponse(
        // Role name, between 2 and 128 chars, 'wire_' prefix is reserved for roles designed
        // by Wire (i.e., no custom roles can have the same prefix)
        @SerialName("conversation_role")
        val conversationRole: String?,

        @SerialName("service")
        val service: ServiceReferenceResponse?,

        //@SerialName("status") val status
        //@SerialName("status_ref") val status
        //@SerialName("status_time") val status

        @SerialName("otr_muted_ref")
        val otrMutedReference: String?,

        @SerialName("otr_muted_status")
        val otrMutedStatus: Int?,

        @SerialName("hidden")
        val hidden: Boolean?,

        @SerialName("hidden_ref")
        val hiddenReference: String?,

        @SerialName("qualified_id")
        override val userId: UserId,

        @SerialName("otr_archived")
        val otrArchived: Boolean?,

        @SerialName("otr_ref")
        val otrArchivedReference: String?,

        @SerialName("otr_archived_ref")
        val otrArchiveReference: String?
) : ConversationMemberResponse

@Serializable
data class ConversationOtherMembersResponse(
        @SerialName("service")
        val service: ServiceReferenceResponse?,

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
