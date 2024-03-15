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

package com.wire.kalium.network.api.v4.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationResponseV3
import com.wire.kalium.network.api.base.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.base.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.base.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.base.model.ApiModelMapper
import com.wire.kalium.network.api.base.model.ApiModelMapperImpl
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.GenerateGuestLinkRequest
import com.wire.kalium.network.api.base.model.JoinConversationRequestV4
import com.wire.kalium.network.api.v3.authenticated.ConversationApiV3
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.handleUnsuccessfulResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapFederationResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.utils.io.errors.IOException

internal open class ConversationApiV4 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl()
) : ConversationApiV3(authenticatedNetworkClient) {

    override suspend fun createNewConversation(createConversationRequest: CreateConversationRequest) =
        wrapKaliumResponse<ConversationResponseV3>(unsuccessfulResponseOverride = { response ->
            wrapFederationResponse(response) { handleUnsuccessfulResponse(response) }
        }) {
            httpClient.post(PATH_CONVERSATIONS) {
                setBody(apiModelMapper.toApiV3(createConversationRequest))
            }
        }.mapSuccess { conversationResponseV4 ->
            apiModelMapper.fromApiV3(conversationResponseV4)
        }

    override suspend fun joinConversation(
        code: String,
        key: String,
        uri: String?,
        password: String?
    ): NetworkResponse<ConversationMemberAddedResponse> =

        httpClient.preparePost("$PATH_CONVERSATIONS/$PATH_JOIN") {
            setBody(JoinConversationRequestV4(code, key, uri, password))
        }.execute { httpResponse ->
            handleConversationMemberAddedResponse(httpResponse)
        }

    override suspend fun fetchLimitedInformationViaCode(
        code: String,
        key: String
    ): NetworkResponse<ConversationCodeInfo> =
        wrapKaliumResponse {
            httpClient.get("$PATH_CONVERSATIONS/$PATH_JOIN") {
                parameter(QUERY_KEY_CODE, code)
                parameter(QUERY_KEY_KEY, key)
            }
        }

    override suspend fun addMember(
        addParticipantRequest: AddConversationMembersRequest,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberAddedResponse> = try {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS") {
            setBody(addParticipantRequest)
        }.let { response ->
            wrapFederationResponse(response) { handleConversationMemberAddedResponse(response) }
        }
    } catch (e: IOException) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    override suspend fun generateGuestRoomLink(
        conversationId: ConversationId,
        password: String?
    ): NetworkResponse<EventContentDTO.Conversation.CodeUpdated> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/${conversationId.value}/$PATH_CODE") {
                setBody(GenerateGuestLinkRequest(password))
            }
        }

    override suspend fun sendTypingIndicatorNotification(
        conversationId: ConversationId,
        typingIndicatorMode: TypingIndicatorStatusDTO
    ): NetworkResponse<Unit> =
        wrapKaliumResponse {
            httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_TYPING_NOTIFICATION") {
                setBody(typingIndicatorMode)
            }
        }
}
