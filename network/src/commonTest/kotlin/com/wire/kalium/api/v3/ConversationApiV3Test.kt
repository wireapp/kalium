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

package com.wire.kalium.api.v3

import com.wire.kalium.api.ApiTest
import com.wire.kalium.model.EventContentDTOJson
import com.wire.kalium.model.conversation.ConversationResponseJson
import com.wire.kalium.model.conversation.CreateConversationRequestJson
import com.wire.kalium.model.conversation.UpdateConversationAccessRequestJson
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessRequest
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationAccessResponse
import com.wire.kalium.network.api.base.model.ConversationAccessDTO
import com.wire.kalium.network.api.base.model.ConversationAccessRoleDTO
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v3.authenticated.ConversationApiV3
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
internal class ConversationApiV3Test : ApiTest() {

    @Test
    fun givenACreateNewConversationRequest_whenCallingCreateNewConversaton_thenTheRequestShouldBeConfiguredOK() = runTest {
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
        val conversationApi: ConversationApi = ConversationApiV3(networkClient)
        val result = conversationApi.createNewConversation(CREATE_CONVERSATION_REQUEST.serializableData)

        assertTrue(result.isSuccessful())
    }

    @Test
    fun whenUpdatingAccessRole_thenTheRequestShouldBeConfiguredCorrectly() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE),
            setOf(ConversationAccessRoleDTO.TEAM_MEMBER)
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.NoContent,
            assertion = {
                assertPut()
                assertPathEqual("/conversations/anta.wire.link/ebafd3d4-1548-49f2-ac4e-b2757e6ca44b/access")
                assertJsonBodyContent(ACCESS_ROLE_UPDATE_REQUEST.rawJson)
            }
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            accessRoles
        )
    }

    @Test
    fun givenAccessUnchangedResponse_whenUpdatingAccessRole_thenAccessUnchangedIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE),
            setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            "",
            statusCode = HttpStatusCode.NoContent
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            accessRoles
        ).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUnchanged>>(it)
        }
    }

    @Test
    fun givenSuccessAccessUpdateResponse_whenUpdatingAccessRole_thenAccessUpdateEventIsPropagated() = runTest {
        val accessRoles = UpdateConversationAccessRequest(
            setOf(ConversationAccessDTO.PRIVATE, ConversationAccessDTO.INVITE),
            setOf()
        )
        val networkClient = mockAuthenticatedNetworkClient(
            EventContentDTOJson.validAccessUpdate.rawJson,
            statusCode = HttpStatusCode.OK
        )

        val conversationApi = ConversationApiV3(networkClient)
        conversationApi.updateAccess(
            ConversationId("ebafd3d4-1548-49f2-ac4e-b2757e6ca44b", "anta.wire.link"),
            accessRoles
        ).also {
            assertIs<NetworkResponse.Success<UpdateConversationAccessResponse.AccessUpdated>>(it)
        }
    }

    private companion object {
        const val PATH_CONVERSATIONS = "/conversations"
        val CREATE_CONVERSATION_RESPONSE = ConversationResponseJson.v3.rawJson
        val CREATE_CONVERSATION_REQUEST = CreateConversationRequestJson.v3
        val ACCESS_ROLE_UPDATE_REQUEST = UpdateConversationAccessRequestJson.v3
    }
}
