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
import com.wire.kalium.network.api.base.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.base.authenticated.conversation.UpdateConversationProtocolResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v4.authenticated.ConversationApiV4
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
@OptIn(ExperimentalCoroutinesApi::class)
internal class ConversationApiV4Test : ApiTest() {

    @Test
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
    }
}
