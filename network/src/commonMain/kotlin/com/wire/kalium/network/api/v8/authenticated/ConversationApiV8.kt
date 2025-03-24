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
import com.wire.kalium.network.api.model.ApiModelMapper
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.v7.authenticated.ConversationApiV7

internal open class ConversationApiV8 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl()
) : ConversationApiV7(authenticatedNetworkClient) {
    /**
     * returns 201 when a new conversation is created or 200 if the conversation already existed
     */
    override suspend fun createNewConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse<ConversationResponseV8> {
        httpClient.post(PATH_CONVERSATIONS) {
            setBody(apiModelMapper.toApiV8(createConversationRequest))
        }
    }.mapSuccess {
        apiModelMapper.fromApiV8(it)
    }
}
