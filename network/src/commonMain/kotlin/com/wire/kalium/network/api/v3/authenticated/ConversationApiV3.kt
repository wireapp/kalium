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

package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTOV3
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV3
import com.wire.kalium.network.api.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.ApiModelMapper
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import okio.IOException

internal open class ConversationApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl()
) : ConversationApiV2(authenticatedNetworkClient) {

    override suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse<ConversationResponseDTOV3> {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }.mapSuccess {
            ConversationResponseDTO(
                conversationsFound = it.conversationsFound.map { conversationFound ->
                    apiModelMapper.fromApiV3(conversationFound)
                },
                conversationsNotFound = it.conversationsNotFound,
                conversationsFailed = it.conversationsFailed
            )
        }

    /**
     * returns 201 when a new conversation is created or 200 if the conversation already existed
     */
    override suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse<ConversationResponseV3> {
        httpClient.post(PATH_CONVERSATIONS) {
            setBody(apiModelMapper.toApiV3(createConversationRequest))
        }
    }.mapSuccess {
        apiModelMapper.fromApiV3(it)
    }

    override suspend fun updateAccess(
        conversationId: ConversationId,
        updateConversationAccessRequest: UpdateConversationAccessRequest
    ): NetworkResponse<UpdateConversationAccessResponse> = try {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_ACCESS") {
            setBody(apiModelMapper.toApiV3(updateConversationAccessRequest))
        }.let { httpResponse ->
            when (httpResponse.status) {
                HttpStatusCode.NoContent -> NetworkResponse.Success(
                    UpdateConversationAccessResponse.AccessUnchanged,
                    httpResponse
                )
                else -> wrapKaliumResponse<EventContentDTO.Conversation.AccessUpdate> { httpResponse }
                    .mapSuccess {
                        UpdateConversationAccessResponse.AccessUpdated(it)
                    }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }
}
