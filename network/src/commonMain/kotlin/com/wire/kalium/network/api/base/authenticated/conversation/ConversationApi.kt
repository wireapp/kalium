/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.base.authenticated.conversation.guestroomlink.GenerateGuestRoomLinkResponse
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.ServiceAddedResponse
import com.wire.kalium.network.api.base.model.SubconversationId
import com.wire.kalium.network.api.base.model.TeamId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

@Suppress("TooManyFunctions")
interface ConversationApi {

    /**
     * Fetch conversations id's in a paginated fashion, including federated conversations
     */
    suspend fun fetchConversationsIds(
        pagingState: String?
    ): NetworkResponse<ConversationPagingResponse>

    /**
     * Fetch conversations details by id's, including federated conversations
     */
    suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO>

    suspend fun fetchConversationDetails(
        conversationId: ConversationId
    ): NetworkResponse<ConversationResponse>

    suspend fun fetchGlobalTeamConversationDetails(
        selfUserId: UserId,
        teamId: TeamId
    ): NetworkResponse<ConversationResponse>

    suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse>

    suspend fun createOne2OneConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse>

    suspend fun addMember(
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedResponse>

    suspend fun addService(
        addServiceRequest: AddServiceRequest,
        conversationId: ConversationId
    ): NetworkResponse<ServiceAddedResponse>

    suspend fun removeMember(
        userId: UserId,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberRemovedResponse>

    suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId
    ): NetworkResponse<Unit>

    suspend fun updateAccess(
        conversationId: ConversationId,
        updateConversationAccessRequest: UpdateConversationAccessRequest
    ): NetworkResponse<UpdateConversationAccessResponse>

    suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        conversationMemberRoleDTO: ConversationMemberRoleDTO
    ): NetworkResponse<Unit>

    suspend fun updateConversationName(
        conversationId: QualifiedID,
        conversationName: String
    ): NetworkResponse<ConversationRenameResponse>

    suspend fun fetchGroupInfo(
        conversationId: QualifiedID
    ): NetworkResponse<ByteArray>

    suspend fun joinConversation(
        code: String,
        key: String,
        uri: String?
    ): NetworkResponse<ConversationMemberAddedResponse>

    suspend fun fetchLimitedInformationViaCode(
        code: String,
        key: String
    ): NetworkResponse<LimitedConversationInfo>

    suspend fun fetchSubconversationDetails(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<SubconversationResponse>

    suspend fun fetchSubconversationGroupInfo(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<ByteArray>

    suspend fun deleteSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId,
        deleteRequest: SubconversationDeleteRequest
    ): NetworkResponse<Unit>

    suspend fun leaveSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<Unit>

    suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: ConversationReceiptModeDTO
    ): NetworkResponse<UpdateConversationReceiptModeResponse>

    suspend fun generateGuestRoomLink(conversationId: ConversationId): NetworkResponse<GenerateGuestRoomLinkResponse>

    suspend fun revokeGuestRoomLink(conversationId: ConversationId): NetworkResponse<Unit>
}
