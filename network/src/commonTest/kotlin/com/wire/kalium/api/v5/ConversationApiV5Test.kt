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

package com.wire.kalium.api.v5

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.model.conversation.SubconversationDeleteRequestJson
import com.wire.kalium.model.conversation.SubconversationDetailsResponseJson
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationDeleteRequest
import com.wire.kalium.network.api.base.authenticated.conversation.SubconversationResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v5.authenticated.ConversationApiV5
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs

internal class ConversationApiV5Test : ApiTest() {


    @Test
    fun givenRequest_whenFetchingSubconversationDetails_thenRequestIsConfiguredCorrectly() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            SubconversationDetailsResponseJson.v5.rawJson,
            statusCode = HttpStatusCode.OK,
            assertion = {
                assertGet()
                assertPathEqual(
                    "/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/subconversations/sub"
                )
            }
        )

        val conversationApi = ConversationApiV5(networkClient)
        conversationApi.fetchSubconversationDetails(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub"
        )
    }

    @Test
    fun givenSuccessSubconversationDetails_whenFetchingSubconversationDetails_thenResponseIsParsedCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SubconversationDetailsResponseJson.v5.rawJson,
                statusCode = HttpStatusCode.OK
            )

            val conversationApi = ConversationApiV5(networkClient)
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

        val conversationApi = ConversationApiV5(networkClient)
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
        val conversationApi = ConversationApiV5(networkClient)
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

        val conversationApi = ConversationApiV5(networkClient)
        conversationApi.leaveSubconversation(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            "sub",
        )
    }
}
