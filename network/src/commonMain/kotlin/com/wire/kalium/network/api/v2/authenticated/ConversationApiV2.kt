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

package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import okio.IOException

internal open class ConversationApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV0(authenticatedNetworkClient) {
    override suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }

    override suspend fun addMember(
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedResponse> = try {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS") {
            setBody(addParticipantRequest)
        }.let { response ->
            handleConversationMemberAddedResponse(response)
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }
}
