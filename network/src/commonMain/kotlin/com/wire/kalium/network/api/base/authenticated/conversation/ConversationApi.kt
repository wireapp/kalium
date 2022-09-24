package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationAccessInfoDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.UpdateConversationAccessResponse
import com.wire.kalium.network.utils.NetworkResponse

interface ConversationApi {

    /**
     * Fetch conversations id's in a paginated fashion, including federated conversations
     */
    suspend fun fetchConversationsIds(pagingState: String?): NetworkResponse<ConversationPagingResponse>

    /**
     * Fetch conversations details by id's, including federated conversations
     */
    suspend fun fetchConversationsListDetails(conversationsIds: List<ConversationId>): NetworkResponse<ConversationResponseDTO>

    suspend fun fetchConversationDetails(conversationId: ConversationId): NetworkResponse<ConversationResponse>

    suspend fun createNewConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse>

    suspend fun createOne2OneConversation(createConversationRequest: CreateConversationRequest): NetworkResponse<ConversationResponse>

    suspend fun addMember(
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedDTO>

    suspend fun removeMember(
        userId: UserId,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberRemovedDTO>

    suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId
    ): NetworkResponse<Unit>

    suspend fun updateAccessRole(
        conversationId: ConversationId,
        conversationAccessInfoDTO: ConversationAccessInfoDTO
    ): NetworkResponse<UpdateConversationAccessResponse>

    suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        conversationMemberRoleDTO: ConversationMemberRoleDTO
    ): NetworkResponse<Unit>
}
