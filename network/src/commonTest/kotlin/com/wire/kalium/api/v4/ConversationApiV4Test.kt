/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.api.v4

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.conversation.ConversationResponseJson
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConversationApiV4Test : ApiTest() {

    @Test
    fun givenACreateNewConversationRequest_whenCallingCreateNewConversation_thenTheResponseShouldMapFailedToAddCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                CREATE_CONVERSATION_RESPONSE,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertJson()
                    assertPost()
                    assertPathEqual(PATH_CONVERSATIONS)
                    assertJsonBodyContent(CREATE_CONVERSATION_REQUEST.rawJson)
                }
            )
            val conversationApi = ConversationApiV4(networkClient)
            val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

            assertTrue(result.isSuccessful())
            assertTrue(result.value.failedToAdd.isNotEmpty())
            assertEquals(result.value.failedToAdd.first(), UserId("failedId", "failedDomain"))
        }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v4_withFailedToAdd.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v3
    }
}
