package com.wire.kalium.api.conversation

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
        val id: ConversationIdResponse,

        @SerialName("type")
        val type: Int,

        @SerialName("message_timer")
        val messageTimer: Int
)

@Serializable
data class ConversationIdResponse(
        @SerialName("id")
        val value: String,

        @SerialName("domain")
        val domain: String
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
        @SerialName("hidden_ref")
        val hiddenReference: String?,

        @SerialName("service")
        val service: ServiceReferenceResponse?,

        @SerialName("otr_muted_ref")
        val otrMutedReference: String?,

        @SerialName("hidden")
        val hidden: Boolean?,

        @SerialName("qualified_id")
        override val userId: UserIdResponse,

        @SerialName("otr_archived")
        val otrArchived: Boolean?,

        @SerialName("otr_muted")
        val otrMuted: Boolean?,

        @SerialName("otr_archived_ref")
        val otrArchiveReference: String?
) : ConversationMemberResponse

@Serializable
data class ConversationOtherMembersResponse(
        @SerialName("service")
        val service: ServiceReferenceResponse?,

        @SerialName("qualified_id")
        override val userId: UserIdResponse,
) : ConversationMemberResponse

interface ConversationMemberResponse {
        val userId: UserIdResponse
}

@Serializable
data class UserIdResponse(
        @SerialName("id")
        val value: String,

        @SerialName("domain")
        val domain: String
)

@Serializable
data class ServiceReferenceResponse(
        @SerialName("id")
        val id: String,

        @SerialName("provider")
        val provider: String
)
