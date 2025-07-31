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
package com.wire.kalium.api.v10

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.mocks.responses.conversation.CreateConversationRequestJson
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.v10.authenticated.ConversationApiV10
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

internal class ConversationApiV10Test : ApiTest() {

    @Test
    fun whenCallingCreateConversation_thenTheResponseShouldBeParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            CREATE_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("/conversations")
                assertJsonBodyContent(CREATE_CONVERSATION_REQUEST.rawJson)
            }
        )
        val conversationApi: ConversationApi = ConversationApiV10(networkClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    private companion object {
        private val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v10()
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v8.rawJson
    }
}
