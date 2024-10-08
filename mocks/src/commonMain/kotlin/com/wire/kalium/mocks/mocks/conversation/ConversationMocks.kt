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
package com.wire.kalium.mocks.mocks.conversation

import com.wire.kalium.mocks.mocks.domain.DomainMocks
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationMembersResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationPagingResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.model.ConversationAccessDTO
import com.wire.kalium.network.api.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId

object ConversationMocks {

    val conversationId = ConversationId("conversation_id", DomainMocks.domain)

    val conversation = ConversationResponse(
        "creator",
        ConversationMembersResponse(
            ConversationMemberDTO.Self(UserId("someValue", "someDomain"), "wire_member"),
            emptyList()
        ),
        "group name",
        conversationId,
        null,
        0UL,
        ConversationResponse.Type.GROUP,
        0,
        null,
        ConvProtocol.PROTEUS,
        lastEventTime = "2024-03-30T15:36:00.000Z",
        access = setOf(ConversationAccessDTO.INVITE, ConversationAccessDTO.CODE),
        accessRole = setOf(
            ConversationAccessRoleDTO.GUEST,
            ConversationAccessRoleDTO.TEAM_MEMBER,
            ConversationAccessRoleDTO.NON_TEAM_MEMBER
        ),
        mlsCipherSuiteTag = null,
        receiptMode = ReceiptMode.DISABLED
    )

    val conversationListIdsResponse = ConversationPagingResponse(
        conversationsIds = listOf(
            conversationId,
            ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
        ),
        hasMore = false,
        pagingState = "AQ=="
    )

    val conversationsDetailsRequest = ConversationsDetailsRequest(
        conversationsIds = listOf(
            conversationId,
            ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
        )
    )
}
