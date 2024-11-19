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

package com.wire.kalium.api.v7

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v7.authenticated.ConversationApiV7
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

internal class ConversationApiV7Test : ApiTest() {

    @Test
    fun whenCallingFetchMlsOneToOneConversation_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            FETCH_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual("/one2one-conversations/${USER_ID.domain}/${USER_ID.value}")
            }
        )
        val conversationApi = ConversationApiV7(networkClient)
        conversationApi.fetchMlsOneToOneConversation(USER_ID)
    }

    @Test
    fun given200Response_whenCallingFetchMlsOneToOneConversation_thenResponseIsParsedCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                FETCH_CONVERSATION_RESPONSE,
                statusCode = HttpStatusCode.OK
            )
            val conversationApi = ConversationApiV7(networkClient)

            val fetchMlsOneToOneConversation = conversationApi.fetchMlsOneToOneConversation(USER_ID)
            assertTrue(fetchMlsOneToOneConversation.isSuccessful())
        }

    companion object {
        val USER_ID = UserId("id", "domain")
        val FETCH_CONVERSATION_RESPONSE = ConversationResponseJson.v6.rawJson
    }
}
