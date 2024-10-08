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
package com.wire.kalium.mocks.requests

import com.wire.kalium.mocks.extensions.toJsonString
import com.wire.kalium.mocks.mocks.conversation.ConversationMocks
import com.wire.kalium.mocks.responses.CommonResponses
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponseDTO
import com.wire.kalium.network.utils.TestRequestHandler
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode

object ConversationRequests {
    private const val PATH_CONVERSATION_ID_LIST = "${CommonResponses.BASE_PATH_V1}conversations/list-ids"
    private const val PATH_CONVERSATIONS = "${CommonResponses.BASE_PATH_V1}conversations"
    private const val PATH_CONVERSATIONS_LIST_V2 = "${CommonResponses.BASE_PATH_V1}/conversations/list/v2"

    private val conversationIdListApiRequestSuccess = TestRequestHandler(
        path = PATH_CONVERSATION_ID_LIST,
        httpMethod = HttpMethod.Post,
        responseBody = ConversationMocks.conversationListIdsResponse.toJsonString(),
        statusCode = HttpStatusCode.OK,
    )

    private val createConversationRequestSuccess = TestRequestHandler(
        path = PATH_CONVERSATIONS,
        httpMethod = HttpMethod.Post,
        responseBody = ConversationResponseJson.v0().rawJson,
        statusCode = HttpStatusCode.OK,
    )

    private val getConversationDetailsListRequestSuccess = TestRequestHandler(
        path = PATH_CONVERSATIONS_LIST_V2,
        httpMethod = HttpMethod.Post,
        responseBody = ConversationResponseDTO(
            conversationsFound = listOf(ConversationMocks.conversation),
            conversationsNotFound = emptyList(),
            conversationsFailed = emptyList()
        ).toJsonString(),
        statusCode = HttpStatusCode.OK,
    )

    val conversationsRequestResponseSuccess = listOf(
        conversationIdListApiRequestSuccess,
        createConversationRequestSuccess,
        getConversationDetailsListRequestSuccess
    )
}
