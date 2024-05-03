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

package com.wire.kalium.api.v0.message

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.model.QualifiedSendMessageRequestJson
import com.wire.kalium.model.QualifiedSendMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
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
internal class QualifiedMessageApiV0Test : ApiTest() {

    @Test
    fun givenAValid_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE.rawJson,
                statusCode = HttpStatusCode.OK,
                assertion =
                {
                    assertPost()
                    assertXProtobuf()
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )

            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.qualifiedSendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID
            )

            assertTrue(response.isSuccessful())
            assertEquals(response.value, SUCCESS_RESPONSE.serializableData)
        }

    @Test
    fun givenDeletedUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(
        DELETED_ERROR_RESPONSE
    )

    @Test
    fun givenRedundantUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(
        REDUNDANT_ERROR_RESPONSE
    )

    @Test
    fun givenMissingUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(
        MISSING_ERROR_RESPONSE
    )

    private fun errorCaseTest(errorResponse: ValidJsonProvider<QualifiedSendMessageResponse.MissingDevicesResponse>) =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                errorResponse.rawJson,
                statusCode = HttpStatusCode.PreconditionFailed,
                assertion =
                {
                    assertPost()
                    assertXProtobuf()
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )

            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.qualifiedSendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID
            )

            assertFalse(response.isSuccessful())
            assertTrue(response.kException is ProteusClientsChangedError)
            assertEquals(
                (response.kException as ProteusClientsChangedError).errorBody,
                errorResponse.serializableData
            )
        }

    private companion object {
        val TEST_CONVERSATION_ID = ConversationId("33d8dee6-7a55-4551-97d2-bd7a5160cd4e", "domain.com")
        val SEND_MESSAGE_PATH = "/conversations/${TEST_CONVERSATION_ID.domain}/${TEST_CONVERSATION_ID.value}/proteus/messages"
        val DEFAULT_PARAMETERS_RESPONSE = QualifiedSendMessageRequestJson.validDefaultParameters
        val MISSING_ERROR_RESPONSE = QualifiedSendMessageResponseJson.missingUsersResponse
        val DELETED_ERROR_RESPONSE = QualifiedSendMessageResponseJson.deletedUsersResponse
        val REDUNDANT_ERROR_RESPONSE = QualifiedSendMessageResponseJson.redundantUsersResponse
        val SUCCESS_RESPONSE = QualifiedSendMessageResponseJson.validMessageSentJson
    }
}
