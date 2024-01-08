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

package com.wire.kalium.api.v2

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.model.conversation.ConversationDetailsResponse
import com.wire.kalium.model.conversation.ConversationListIdsResponseJson
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.ReceiptMode
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class ConversationApiV2Test : ApiTest() {
    @Test
    fun givenFetchConversationsDetails_whenCallingFetchWithIdList_thenTheRequestShouldBeConfiguredOK() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            CONVERSATION_DETAILS_RESPONSE.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertJson()
                assertJsonBodyContent(CREATE_CONVERSATION_IDS_REQUEST.rawJson)
                assertPathEqual(PATH_CONVERSATIONS_LIST)
            }
        )

        val conversationApi = ConversationApiV2(networkClient)
        conversationApi.fetchConversationsListDetails(
            listOf(
                ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
                ConversationId("f4680835-2cfe-4d4d-8491-cbb201bd5c2b", "anta.wire.link")
            )
        )
    }

    @Test
    fun whenAddingMemberToGroup_thenTheMemberShouldBeAddedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val userId = UserId("userId", "userDomain")
        val request = AddConversationMembersRequest(listOf(userId), "Member")

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validMemberJoin.rawJson, statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_MEMBERS")
            }
        )
        val conversationApi = ConversationApiV2(networkClient)
        val response = conversationApi.addMember(request, conversationId)

        assertTrue(response.isSuccessful())
    }

    @Test
    fun givenNullReceiptMode_whenFetchingConversationDetails_thenShouldReturnDisabled() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ConversationDetailsResponse.withNullReceiptMode.rawJson, statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV2(networkClient)

        val response = conversationApi.fetchConversationsListDetails(listOf())

        assertTrue(response.isSuccessful())
        assertEquals(ReceiptMode.DISABLED, response.value.conversationsFound.first().receiptMode)
    }

    private companion object {
        const val PATH_CONVERSATIONS_LIST = "/conversations/list"
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_MEMBERS = "members"
        val CREATE_CONVERSATION_IDS_REQUEST = ConversationListIdsResponseJson.validRequestIds
        val CONVERSATION_DETAILS_RESPONSE = ConversationDetailsResponse.validGetDetailsForIds
    }
}
