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

package com.wire.kalium.api.v0.message

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.model.QualifiedSendMessageRequestJson
import com.wire.kalium.model.QualifiedSendMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v0.authenticated.MessageApiV0
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
@IgnoreIOS
class QualifiedMessageApiV0Test : ApiTest {

    @Test
    fun givenAValidIgnoreAlloption_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {

        }

    @Test
    fun givenFailedToSentUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            QualifiedSendMessageResponseJson.failedSentUsersResponse.rawJson,
            statusCode = HttpStatusCode.Created,
            assertion =
            {
                assertPost()
                assertJson()
                assertPathEqual(SEND_MESSAGE_PATH)
            }
        )

        val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
        val response = messageApi.qualifiedSendMessage(
            QualifiedSendMessageRequestJson.validDefaultParameters.serializableData,
            TEST_CONVERSATION_ID
        )

        assertFalse(response.isSuccessful())
        assertTrue(response.kException is ProteusClientsChangedError)
        assertEquals(
            (response.kException as ProteusClientsChangedError).errorBody,
            QualifiedSendMessageResponseJson.failedSentUsersResponse.serializableData
        )
    }

    private companion object {
        val TEST_CONVERSATION_ID = ConversationId("33d8dee6-7a55-4551-97d2-bd7a5160cd4e", "domain.com")
        val SEND_MESSAGE_PATH = "/conversations/${TEST_CONVERSATION_ID.domain}/${TEST_CONVERSATION_ID.value}/proteus/messages"
        val TEST_USER_LIST = listOf("user_1", "user_2", "user_3")
        val DEFAULT_PARAMETERS_RESPONSE = QualifiedSendMessageRequestJson.validDefaultParameters
        val SUCCESS_RESPONSE = QualifiedSendMessageResponseJson.validMessageSentJson
        val MISSING_ERROR_RESPONSE = QualifiedSendMessageResponseJson.missingUsersResponse
        val DELETED_ERROR_RESPONSE = QualifiedSendMessageResponseJson.deletedUsersResponse
        val REDUNDANT_ERROR_RESPONSE = QualifiedSendMessageResponseJson.redundantUsersResponse
    }
}
