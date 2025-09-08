/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.stubs

import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.AddServiceRequest
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
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.api.model.ServiceAddedResponse
import com.wire.kalium.network.api.model.SubconversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.utils.NetworkResponse

open class ConversationApiStub : ConversationApi {
    override suspend fun fetchConversationsIds(pagingState: String?): NetworkResponse<ConversationPagingResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchConversationsListDetails(conversationsIds: List<ConversationId>): NetworkResponse<ConversationResponseDTO> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchConversationDetails(conversationId: ConversationId): NetworkResponse<ConversationResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun addMember(
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun addService(
        addServiceRequest: AddServiceRequest,
        conversationId: ConversationId
    ): NetworkResponse<ServiceAddedResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun removeMember(
        userId: UserId,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberRemovedResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateConversationMemberState(
        memberUpdateRequest: MemberUpdateDTO,
        conversationId: ConversationId
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateAccess(
        conversationId: ConversationId,
        updateConversationAccessRequest: UpdateConversationAccessRequest
    ): NetworkResponse<UpdateConversationAccessResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateConversationMemberRole(
        conversationId: ConversationId,
        userId: UserId,
        conversationMemberRoleDTO: ConversationMemberRoleDTO
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateConversationName(
        conversationId: QualifiedID,
        conversationName: String
    ): NetworkResponse<ConversationRenameResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchGroupInfo(conversationId: QualifiedID): NetworkResponse<ByteArray> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun joinConversation(
        code: String,
        key: String,
        uri: String?,
        password: String?
    ): NetworkResponse<ConversationMemberAddedResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchLimitedInformationViaCode(
        code: String,
        key: String
    ): NetworkResponse<ConversationCodeInfo> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchMlsOneToOneConversation(userId: UserId): NetworkResponse<ConversationResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchSubconversationDetails(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<SubconversationResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun fetchSubconversationGroupInfo(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<ByteArray> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun deleteSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId,
        deleteRequest: SubconversationDeleteRequest
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun leaveSubconversation(
        conversationId: ConversationId,
        subconversationId: SubconversationId
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateReceiptMode(
        conversationId: ConversationId,
        receiptMode: ConversationReceiptModeDTO
    ): NetworkResponse<UpdateConversationReceiptModeResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun generateGuestRoomLink(
        conversationId: ConversationId,
        password: String?
    ): NetworkResponse<EventContentDTO.Conversation.CodeUpdated> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun revokeGuestRoomLink(conversationId: ConversationId): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateMessageTimer(
        conversationId: ConversationId,
        messageTimer: Long?
    ): NetworkResponse<EventContentDTO.Conversation.MessageTimerUpdate> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun sendTypingIndicatorNotification(
        conversationId: ConversationId,
        typingIndicatorMode: TypingIndicatorStatusDTO
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateProtocol(
        conversationId: ConversationId,
        protocol: ConvProtocol
    ): NetworkResponse<UpdateConversationProtocolResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun updateChannelAddPermission(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermissionDTO
    ): NetworkResponse<UpdateChannelAddPermissionResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun guestLinkInfo(conversationId: ConversationId): NetworkResponse<ConversationInviteLinkResponse> {
        TODO("Test stub function was not implemented.")
    }

    override suspend fun resetMlsConversation(
        groupId: String,
        epoch: ULong
    ): NetworkResponse<Unit> {
        TODO("Test stub function was not implemented.")
    }
}
