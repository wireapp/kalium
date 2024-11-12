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

package com.wire.kalium.network.api.v7.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV3
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseV6
import com.wire.kalium.network.api.authenticated.conversation.CreateConversationRequest
import com.wire.kalium.network.api.model.ApiModelMapper
import com.wire.kalium.network.api.model.ApiModelMapperImpl
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v6.authenticated.ConversationApiV6
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.mapSuccess
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

internal open class ConversationApiV7 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val apiModelMapper: ApiModelMapper = ApiModelMapperImpl(),
) : ConversationApiV6(authenticatedNetworkClient) {
    override suspend fun createOne2OneConversation(
        createConversationRequest: CreateConversationRequest
    ): NetworkResponse<ConversationResponse> = wrapKaliumResponse<ConversationResponseV3> {
        httpClient.post(PATH_ONE_2_ONE_CONVERSATIONS) {
            setBody(apiModelMapper.toApiV3(createConversationRequest))
        }
    }.mapSuccess {
        apiModelMapper.fromApiV3(it)
    }

    override suspend fun fetchMlsOneToOneConversation(userId: UserId): NetworkResponse<ConversationResponse> =
        wrapKaliumResponse<ConversationResponseV6> {
            httpClient.get("$PATH_ONE_2_ONE_CONVERSATIONS/${userId.domain}/${userId.value}")
        }.mapSuccess {
            apiModelMapper.fromApiV6(it)
        }

    protected companion object {
        const val PATH_ONE_2_ONE_CONVERSATIONS = "one2one-conversations"
    }
}
