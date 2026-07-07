/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

package com.wire.kalium.api.v16

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.EventContentDTOJson
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberRemovedResponse
import com.wire.kalium.network.api.model.ConversationId
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.v15.authenticated.ConversationApiV15
import com.wire.kalium.network.api.v16.authenticated.ConversationApiV16
import com.wire.kalium.network.exceptions.AdminlessConversationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ConversationApiV16Test : ApiTest() {

    @Test
    fun givenRemoveMemberReturnsOk_whenCallingRemoveMember_thenTheMemberShouldBeRemovedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validMemberLeave.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual("$PATH_CONVERSATIONS/$CONVERSATION_DOMAIN/$CONVERSATION_ID/$PATH_MEMBERS/$USER_DOMAIN/$USER_ID")
            }
        )
        val conversationApi = ConversationApiV16(networkClient)
        val response = conversationApi.removeMember(userId, conversationId)

        assertTrue(response.isSuccessful())
        assertIs<ConversationMemberRemovedResponse.Changed>(response.value)
    }

    @Test
    fun givenAdminlessConversationError_whenCallingRemoveMemberWithV16_thenEligibleMembersShouldBePreserved() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ADMINLESS_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.Forbidden
        )
        val conversationApi = ConversationApiV16(networkClient)
        val response = conversationApi.removeMember(userId, conversationId)

        assertIs<NetworkResponse.Error>(response)
        val exception = assertIs<AdminlessConversationError>(response.kException)
        assertEquals(HttpStatusCode.Forbidden.value, exception.errorResponse.code)
        assertEquals("adminless-conversation", exception.errorResponse.label)
        assertEquals(
            listOf(
                UserId("2e910548-4087-4fa4-9bef-320c9d0d4b3d", "staging.zinfra.io"),
                UserId("df2efc16-05cb-4103-9346-e5fe2f29f7a5", "staging.zinfra.io")
            ),
            exception.errorResponse.eligibleMembers
        )
    }

    @Test
    fun givenAdminlessConversationError_whenCallingRemoveMemberWithV15_thenResponseShouldUseGenericError() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ADMINLESS_CONVERSATION_RESPONSE,
            statusCode = HttpStatusCode.Forbidden
        )
        val conversationApi = ConversationApiV15(networkClient)
        val response = conversationApi.removeMember(userId, conversationId)

        assertIs<NetworkResponse.Error>(response)
        assertIs<KaliumException.InvalidRequestError>(response.kException)
    }

    private companion object {
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_MEMBERS = "members"
        const val CONVERSATION_ID = "285b4e4b-2c6a-4834-adce-a9f72f4f85ed"
        const val CONVERSATION_DOMAIN = "staging.zinfra.io"
        const val USER_ID = "1a194b36-6c03-446f-a2d8-1474bd849b3f"
        const val USER_DOMAIN = "staging.zinfra.io"

        val conversationId = ConversationId(CONVERSATION_ID, CONVERSATION_DOMAIN)
        val userId = UserId(USER_ID, USER_DOMAIN)

        val ADMINLESS_CONVERSATION_RESPONSE = """
            {
              "code": 403,
              "eligible_members": [
                {
                  "domain": "staging.zinfra.io",
                  "id": "2e910548-4087-4fa4-9bef-320c9d0d4b3d"
                },
                {
                  "domain": "staging.zinfra.io",
                  "id": "df2efc16-05cb-4103-9346-e5fe2f29f7a5"
                }
              ],
              "label": "adminless-conversation",
              "message": "The conversation would be left without an admin"
            }
        """.trimIndent()
    }
}
