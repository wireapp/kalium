package com.wire.kalium.network.api.base.model

import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseV3
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequestV3
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequestV3

/**
 * Mapping between the base API model and the versioned API models.
 */
internal interface ApiModelMapper {

    fun toApiV3(request: CreateConversationRequest): CreateConversationRequestV3
    fun toApiV3(request: UpdateConversationAccessRequest): UpdateConversationAccessRequestV3
    fun fromApiV3(response: ConversationResponseV3): ConversationResponse
}

internal class ApiModelMapperImpl : ApiModelMapper {

    override fun toApiV3(request: CreateConversationRequest): CreateConversationRequestV3 =
        CreateConversationRequestV3(
            request.qualifiedUsers,
            request.name,
            request.access,
            request.accessRole,
            request.convTeamInfo,
            request.messageTimer,
            request.receiptMode,
            request.conversationRole,
            request.protocol,
            request.creatorClient
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
            response.accessRole,
            response.receiptMode
        )
}
