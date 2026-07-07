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

package com.wire.kalium.network.api.v16.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.AdminlessConversationErrorResponse
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v15.authenticated.ConversationApiV15
import com.wire.kalium.network.exceptions.AdminlessConversationError
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.delete
import io.ktor.http.HttpStatusCode

internal open class ConversationApiV16 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV15(authenticatedNetworkClient) {

    override suspend fun removeMember(
        userId: UserId,
        conversationId: ConversationId
    ): NetworkResponse<ConversationMemberRemovedResponse> =
        wrapRequest(
            customErrorInterceptor = { responseData ->
                val error = runCatching {
                    responseData.parseBody<AdminlessConversationErrorResponse>()
                }.getOrNull()

                if (
                    responseData.status == HttpStatusCode.Forbidden &&
                    error?.label == AdminlessConversationErrorResponse.LABEL
                ) {
                    NetworkResponse.Error(AdminlessConversationError(error))
                } else {
                    null
                }
            },
            successHandler = { response ->
                when (response.status) {
                    HttpStatusCode.OK -> wrapRequest<EventContentDTO.Conversation.MemberLeaveDTO> { response }
                        .mapSuccess { ConversationMemberRemovedResponse.Changed(it) }

                    HttpStatusCode.NoContent -> NetworkResponse.Success(ConversationMemberRemovedResponse.Unchanged, response)
                    else -> wrapRequest { response }
                }
            }
        ) {
            httpClient.delete(
                "$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS/${userId.domain}/${userId.value}"
            )
        }
}
