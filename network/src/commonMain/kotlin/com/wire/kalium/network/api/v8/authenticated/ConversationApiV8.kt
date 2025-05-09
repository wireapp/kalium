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

package com.wire.kalium.network.api.v8.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTOV8
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV8
import com.wire.kalium.network.api.authenticated.conversation.ConversationsDetailsRequest
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.authenticated.conversation.UpdateChannelAddPermissionResponse
import com.wire.kalium.network.api.authenticated.conversation.channel.ChannelAddPermissionDTO
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.ApiModelMapper
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.v7.authenticated.ConversationApiV7
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import okio.IOException

internal open class ConversationApiV8 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl(),
) : ConversationApiV7(authenticatedNetworkClient) {

    override suspend fun fetchConversationDetails(
        conversationId: ConversationId
    ): NetworkResponse<ConversationResponse> =
        wrapKaliumResponse<ConversationResponseV8> {
            httpClient.get(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}"
            )
        }.mapSuccess { conversationResponseV8 ->
            apiModelMapper.fromApiV8(conversationResponseV8)
        }

    override suspend fun fetchConversationsListDetails(
        conversationsIds: List<ConversationId>
    ): NetworkResponse<ConversationResponseDTO> =
        wrapKaliumResponse<ConversationResponseDTOV8> {
            httpClient.post("$PATH_CONVERSATIONS/$PATH_CONVERSATIONS_LIST") {
                setBody(ConversationsDetailsRequest(conversationsIds = conversationsIds))
            }
        }.mapSuccess {
            ConversationResponseDTO(
                conversationsFound = it.conversationsFound.map { conversationFound ->
                    apiModelMapper.fromApiV8(conversationFound)
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
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse<ConversationResponseV8> {
        httpClient.post(PATH_CONVERSATIONS) {
            setBody(apiModelMapper.toApiV8(createConversationRequest))
        }
    }.mapSuccess { conversationResponseV8 ->
        apiModelMapper.fromApiV8(conversationResponseV8)
    }

    override suspend fun updateChannelAddPermission(
        conversationId: ConversationId,
        channelAddPermission: ChannelAddPermissionDTO
    ): NetworkResponse<UpdateChannelAddPermissionResponse> = try {
        httpClient.put("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_ADD_PERMISSION") {
            setBody(channelAddPermission)
        }.let { httpResponse ->
            when (httpResponse.status) {
                HttpStatusCode.NoContent -> NetworkResponse.Success(
                    UpdateChannelAddPermissionResponse.PermissionUnchanged,
                    httpResponse
                )

                else -> wrapKaliumResponse<EventContentDTO.Conversation.ChannelAddPermissionUpdate> { httpResponse }
                    .mapSuccess {
                        UpdateChannelAddPermissionResponse.PermissionUpdated(it)
                    }
            }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    companion object {
        const val PATH_ADD_PERMISSION = "add-permission"
    }
}
