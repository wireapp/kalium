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

package com.wire.kalium.api.v4

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.model.conversation.SendTypingStatusNotificationRequestJson
import com.wire.kalium.network.api.base.authenticated.conversation.AddConversationMembersRequest
import com.wire.kalium.network.api.base.authenticated.conversation.TypingIndicatorStatus
import com.wire.kalium.network.api.base.authenticated.conversation.TypingIndicatorStatusDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.FederationConflictResponse
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.UnreachableRemoteBackends
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class ConversationApiV4Test : ApiTest() {

    @Test
    fun givenACreateNewConversation_whenReturnsNFCGFederationError_thenTheResponseShouldMapToFederationConflictError() =
        runTest {
            val conflictingBackends = listOf("bella.wire.link", "foma.wire.link")
            val response =
                ErrorResponseJson.validFederationConflictingBackends(FederationConflictResponse(conflictingBackends)).rawJson

            val networkClient = mockAuthenticatedNetworkClient(
                response,
                statusCode = HttpStatusCode.Conflict,
                assertion = {
                    assertJson()
                    assertPost()
                    assertPathEqual(PATH_CONVERSATIONS)
                    assertJsonBodyContent(CREATE_CONVERSATION_REQUEST.rawJson)
                }
            )
            val conversationApi = ConversationApiV4(networkClient)
            val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

            assertFalse(result.isSuccessful())
            assertTrue(result.kException is KaliumException.FederationConflictException)
            assertEquals(
                (result.kException as KaliumException.FederationConflictException).errorResponse.nonFederatingBackends,
                conflictingBackends
            )
        }

    @Test
    fun whenAddingMemberToGroup_AndRemoteFailureUnreachable_thenTheMemberShouldBeAddedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val userId = UserId("userId", "userDomain")
        val request = AddConversationMembersRequest(listOf(userId), "Member")

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.jsonProviderMemberJoinFailureUnreachable,
            statusCode = HttpStatusCode.UnreachableRemoteBackends,
            assertion = {
                assertPost()
                assertPathEqual("${PATH_CONVERSATIONS}/${conversationId.domain}/${conversationId.value}/${PATH_MEMBERS}")
            }
        )
        val conversationApi = ConversationApiV4(networkClient)
        val response = conversationApi.addMember(request, conversationId)

        assertFalse(response.isSuccessful())
        assertTrue(response.kException is KaliumException.FederationUnreachableException)
        assertTrue {
            (response.kException as KaliumException.FederationUnreachableException).errorResponse.unreachableBackends.contains(
                "foma.wire.link"
            )
        }
    }

    @Test
    fun givenAddingMembersConversationRequest_whenReturnsFederationConflictError_thenTheResponseShouldMapToFederationError() =
        runTest {
            val conversationId = ConversationId("conversationId", "conversationDomain")
            val userId = UserId("userId", "userDomain")
            val request = AddConversationMembersRequest(listOf(userId), "Member")
            val conflictingBackends = listOf("bella.wire.link", "foma.wire.link")
            val expectedResponse =
                ErrorResponseJson.validFederationConflictingBackends(FederationConflictResponse(conflictingBackends)).rawJson

            val networkClient = mockAuthenticatedNetworkClient(
                expectedResponse,
                statusCode = HttpStatusCode.Conflict,
                assertion = {
                    assertPost()
                    assertPathEqual("${PATH_CONVERSATIONS}/${conversationId.domain}/${conversationId.value}/${PATH_MEMBERS}")
                }
            )
            val conversationApi = ConversationApiV4(networkClient)
            val response = conversationApi.addMember(request, conversationId)

            assertFalse(response.isSuccessful())
            assertTrue(response.kException is KaliumException.FederationConflictException)
            assertEquals(
                (response.kException as KaliumException.FederationConflictException).errorResponse.nonFederatingBackends,
                conflictingBackends
            )
        }

    @Test
    fun givenTypingNotificationRequest_whenSendingStatus_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")
        val request = TypingIndicatorStatusDTO(TypingIndicatorStatus.STARTED)

        val networkClient = mockAuthenticatedNetworkClient(
            ByteArray(0),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPost()
                assertPathEqual("${PATH_CONVERSATIONS}/${conversationId.domain}/${conversationId.value}/${PATH_TYPING_NOTIFICATION}")
                assertJsonBodyContent(SendTypingStatusNotificationRequestJson.createValid(TypingIndicatorStatus.STARTED).rawJson)
            }
        )
        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.sendTypingIndicatorNotification(conversationId, request).also {
            assertIs<NetworkResponse.Success<Unit>>(it)
        }
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_MEMBERS = "members"
        const val PATH_TYPING_NOTIFICATION = "typing"
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v3
    }
}
