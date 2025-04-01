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

package com.wire.kalium.network.api.base.authenticated.conversation

import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
import com.wire.kalium.network.api.authenticated.conversation.ChannelAddPermission
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationRenameResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.MemberUpdateDTO
import com.wire.kalium.network.api.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationReceiptModeResponse
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddPermissionDTO
import com.wire.kalium.network.api.authenticated.conversation.guestroomlink.ConversationInviteLinkResponse
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationMemberRoleDTO
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationReceiptModeDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.authenticated.BaseApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.network.api.model.SubconversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse

@Suppress("TooManyFunctions")
interface ConversationApi : BaseApi {

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

    suspend fun createNewConversation(
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
        uri: String?,
        password: String?
    ): NetworkResponse<ConversationMemberAddedResponse>

    suspend fun fetchLimitedInformationViaCode(
        code: String,
        key: String
    ): NetworkResponse<ConversationCodeInfo>

    suspend fun fetchMlsOneToOneConversation(
        userId: UserId
    ): NetworkResponse<ConversationResponse>

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

    suspend fun generateGuestRoomLink(
        conversationId: ConversationId,
        password: String?
    ): NetworkResponse<EventContentDTO.Conversation.CodeUpdated>

    suspend fun revokeGuestRoomLink(conversationId: ConversationId): NetworkResponse<Unit>

    suspend fun updateMessageTimer(
        conversationId: ConversationId,
        messageTimer: Long?
    ): NetworkResponse<EventContentDTO.Conversation.MessageTimerUpdate>

    suspend fun sendTypingIndicatorNotification(
        conversationId: ConversationId,
        typingIndicatorMode: TypingIndicatorStatusDTO
    ): NetworkResponse<Unit>

    suspend fun updateProtocol(
        conversationId: ConversationId,
        protocol: ConvProtocol
    ): NetworkResponse<UpdateConversationProtocolResponse>

    suspend fun updateChannelPermission(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermissionDTO
    ): NetworkResponse<UpdateChannelAddPermissionResponse>

    suspend fun guestLinkInfo(
        conversationId: ConversationId
    ): NetworkResponse<ConversationInviteLinkResponse>

    companion object {
        fun getApiNotSupportError(apiName: String, apiVersion: String = "4") = NetworkResponse.Error(
            APINotSupported("${this::class.simpleName}: $apiName api is only available on API V$apiVersion")
        )
    }
}
