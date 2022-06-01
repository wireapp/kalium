package com.wire.kalium.network.api.conversation

import com.wire.kalium.network.api.ConversationId
import com.wire.kalium.network.api.UserId
import com.wire.kalium.network.utils.NetworkResponse

interface ConversationApi {

    suspend fun conversationsByBatch(pagingState: String?): NetworkResponse<ConversationPagingResponse>

    suspend fun fetchConversationsDetails(conversationsIds: List<ConversationId>): NetworkResponse<ConversationResponseDTO>

    suspend fun fetchConversationDetails(conversationId: ConversationId): NetworkResponse<ConversationResponse>

    suspend fun removeConversationMember(userId: UserId, conversationId: ConversationId): NetworkResponse<Unit>

    suspend fun createNewConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse>

    suspend fun createOne2OneConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse>

    suspend fun addParticipant(
        addParticipantRequest: AddParticipantRequest,
        conversationId: ConversationId
    ): NetworkResponse<AddParticipantResponse>

    suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId
    ): NetworkResponse<Unit>
}
