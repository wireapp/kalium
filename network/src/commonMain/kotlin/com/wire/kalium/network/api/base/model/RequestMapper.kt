package com.wire.kalium.network.api.base.model

import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequestV3
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequestV3

internal interface RequestMapper {

    fun toApiV3(request: CreateConversationRequest): CreateConversationRequestV3
    fun toApiV3(request: UpdateConversationAccessRequest): UpdateConversationAccessRequestV3
}

internal class RequestMapperImpl : RequestMapper {

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
}
