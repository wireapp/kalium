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

package com.wire.kalium.network.api.model

import com.wire.kalium.network.api.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.authenticated.client.RegisterClientRequest
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV3
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV6
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV8
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequestV3
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequestV8
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequestV3

/**
 * Mapping between the base API model and the versioned API models.
 */
interface ApiModelMapper {

    fun toApiV3(request: CreateConversationRequest): CreateConversationRequestV3
    fun toApiV3(request: UpdateConversationAccessRequest): UpdateConversationAccessRequestV3
    fun toApiV8(request: CreateConversationRequest): CreateConversationRequestV8
    fun fromApiV3(response: ConversationResponseV3): ConversationResponse
    fun fromApiV6(response: ConversationResponseV6): ConversationResponse
    fun fromApiV8(response: ConversationResponseV8): ConversationResponse

    /**
     * Forcing to clients using v8+ to have ConsumableNotifications capability.
     */
    fun toApiV8(request: RegisterClientRequest): RegisterClientRequest
}

class ApiModelMapperImpl : ApiModelMapper {

    override fun toApiV3(request: CreateConversationRequest): CreateConversationRequestV3 =
        CreateConversationRequestV3(
            request.qualifiedUsers,
            request.name,
            request.access,
            request.accessRole,
            request.groupConversationType,
            request.convTeamInfo,
            request.messageTimer,
            request.receiptMode,
            request.conversationRole,
            request.protocol,
            request.creatorClient
        )

    override fun toApiV8(request: CreateConversationRequest): CreateConversationRequestV8 =
        CreateConversationRequestV8(
            request.qualifiedUsers,
            request.name,
            request.access,
            request.accessRole,
            request.groupConversationType,
            request.convTeamInfo,
            request.messageTimer,
            request.receiptMode,
            request.conversationRole,
            request.protocol,
            request.creatorClient
        )

    override fun toApiV8(request: RegisterClientRequest): RegisterClientRequest =
        RegisterClientRequest(
            password = request.password,
            preKeys = request.preKeys,
            lastKey = request.lastKey,
            deviceType = request.deviceType,
            type = request.type,
            label = request.label,
            capabilities = request.capabilities?.also { it.toMutableSet().add(ClientCapabilityDTO.ConsumableNotifications) }?.toList()
                ?: listOf(ClientCapabilityDTO.ConsumableNotifications),
            model = request.model,
            cookieLabel = request.cookieLabel,
            secondFactorVerificationCode = request.secondFactorVerificationCode
        )

    override fun toApiV3(request: UpdateConversationAccessRequest): UpdateConversationAccessRequestV3 =
        UpdateConversationAccessRequestV3(
            request.access,
            request.accessRole
        )

    override fun fromApiV3(response: ConversationResponseV3): ConversationResponse =
        ConversationResponse(
            response.creator,
            response.members,
            response.name,
            response.id,
            response.groupId,
            response.epoch,
            response.type,
            response.messageTimer,
            response.teamId,
            response.protocol,
            response.lastEventTime,
            response.mlsCipherSuiteTag,
            response.access,
            accessRole = response.accessRole ?: response.accessRoleV2 ?: ConversationAccessRoleDTO.DEFAULT_VALUE_WHEN_NULL,
            response.receiptMode
        )

    override fun fromApiV6(response: ConversationResponseV6): ConversationResponse =
        ConversationResponse(
            creator = response.conversation.creator,
            members = response.conversation.members,
            name = response.conversation.name,
            id = response.conversation.id,
            groupId = response.conversation.groupId,
            epoch = response.conversation.epoch,
            type = response.conversation.type,
            messageTimer = response.conversation.messageTimer,
            teamId = response.conversation.teamId,
            protocol = response.conversation.protocol,
            lastEventTime = response.conversation.lastEventTime,
            mlsCipherSuiteTag = response.conversation.mlsCipherSuiteTag,
            access = response.conversation.access,
            accessRole = response.conversation.accessRole,
            receiptMode = response.conversation.receiptMode,
            publicKeys = response.publicKeys,
            conversationGroupType = response.conversation.conversationGroupType
        )

    override fun fromApiV8(response: ConversationResponseV8): ConversationResponse =
        ConversationResponse(
            creator = response.conversation.creator,
            members = response.conversation.members,
            name = response.conversation.name,
            id = response.conversation.id,
            groupId = response.conversation.groupId,
            epoch = response.conversation.epoch,
            type = response.conversation.type,
            messageTimer = response.conversation.messageTimer,
            teamId = response.conversation.teamId,
            protocol = response.conversation.protocol,
            lastEventTime = response.conversation.lastEventTime,
            mlsCipherSuiteTag = response.conversation.mlsCipherSuiteTag,
            access = response.conversation.access,
            accessRole = response.conversation.accessRole,
            receiptMode = response.conversation.receiptMode,
            publicKeys = response.conversation.publicKeys,
            conversationGroupType = response.groupType
        )
}
