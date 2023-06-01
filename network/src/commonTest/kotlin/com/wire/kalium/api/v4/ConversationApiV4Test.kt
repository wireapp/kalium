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
import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.model.conversation.ConversationResponseJson
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.model.conversation.SubconversationDeleteRequestJson
import com.wire.kalium.model.conversation.SubconversationDetailsResponseJson
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.base.model.UserId
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
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

    @Test
    fun givenRequest_whenFetchingSubconversationDetails_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SubconversationDetailsResponseJson.v4.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(
                    "/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub"
                )
            }
        )

        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.fetchSubconversationDetails(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub"
        )
    }

    fun given200Response_whenUpdatingConversationProtocol_thenEventIsParsedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validUpdateProtocol.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertPut()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_PROTOCOL")
            }
        )
        val conversationApi = ConversationApiV4(networkClient)
        val response = conversationApi.updateProtocol(conversationId, ConvProtocol.MIXED)

        assertIs<NetworkResponse.Success<UpdateConversationProtocolResponse>>(response)
        assertIs<UpdateConversationProtocolResponse.ProtocolUpdated>(response.value)
        assertEquals(
            EventContentDTOJson.validUpdateProtocol.serializableData,
            (response.value as UpdateConversationProtocolResponse.ProtocolUpdated).event
        )
    }

    @Test
    fun givenSuccessSubconversationDetails_whenFetchingSubconversationDetails_thenResponseIsParsedCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SubconversationDetailsResponseJson.v4.rawJson,
            statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.fetchSubconversationDetails(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub"
        ).also {
            assertIs<NetworkResponse.Success<SubconversationResponse>>(it)
        }
    }

    @Test
    fun givenRequest_whenFetchingSubconversationGroupInfo_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            "groupinfo".encodeToByteArray(),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(
                    "/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub/groupinfo"
                )
            }
        )

        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.fetchSubconversationGroupInfo(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub"
        ).also {
            assertIs<NetworkResponse.Success<ByteArray>>(it)
        }
    }

    @Test
    fun givenRequest_whenDeletingSubconversation_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ByteArray(0),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual(
                    "/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub"
                )
                assertJsonBodyContent(SubconversationDeleteRequestJson.v4.rawJson)
            }
        )

        val deleteRequest = SubconversationDeleteRequest(43UL, "groupid")
        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.deleteSubconversation(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub",
            deleteRequest
        )
    }

    @Test
    fun givenRequest_whenLeavingSubconversation_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ByteArray(0),
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertDelete()
                assertPathEqual(
                    "/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub/self"
                )
            }
        )

        val conversationApi = ConversationApiV4(networkClient)
        conversationApi.leaveSubconversation(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub",
        )
    }

    fun given204Response_whenUpdatingConversationProtocol_thenEventIsParsedCorrectly() = runTest {
        val conversationId = ConversationId("conversationId", "conversationDomain")

        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_PROTOCOL")
            }
        )
        val conversationApi = ConversationApiV4(networkClient)
        val response = conversationApi.updateProtocol(conversationId, ConvProtocol.MIXED)

        assertIs<NetworkResponse.Success<UpdateConversationProtocolResponse>>(response)
        assertIs<UpdateConversationProtocolResponse.ProtocolUnchanged>(response.value)
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        const val PATH_PROTOCOL = "protocol"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v4_withFailedToAdd.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v3
    }
}
