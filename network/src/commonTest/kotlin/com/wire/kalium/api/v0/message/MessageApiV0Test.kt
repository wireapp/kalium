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
import com.wire.kalium.api.json.ValidJsonProvider
import com.wire.kalium.api.tools.IgnoreIOS
import com.wire.kalium.model.SendMessageRequestJson
import com.wire.kalium.model.SendMessageResponseJson
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapperImpl
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.SendMessageResponse
import com.wire.kalium.network.api.v0.authenticated.MessageApiV0
import com.wire.kalium.network.exceptions.SendMessageError
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
internal class MessageApiV0Test : ApiTest() {
    // valid
    @Test
    fun givenAValidIgnoreAlloption_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertJson()
                    assertQueryParameter("ignore_missing", "true")
                    assertQueryDoesNotExist("report_missing")
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )
            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.IgnoreAll
            )
            assertTrue(response.isSuccessful())
            assertEquals(response.value, SUCCESS_RESPONSE.serializableData)
        }

    @Test
    fun givenAValidReportAll_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertJson()
                    assertQueryDoesNotExist("ignore_missing")
                    assertQueryParameter("report_missing", "true")
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )
            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.ReportAll
            )
            assertTrue(response.isSuccessful())
            assertEquals(response.value, SUCCESS_RESPONSE.serializableData)
        }

    @Test
    fun givenAValidIgnoreSome_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertJson()
                    assertQueryParameter("ignore_missing", "user_1,user_2,user_3")
                    assertQueryDoesNotExist("report_missing")
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )
            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.IgnoreSome(TEST_USER_LIST)
            )
            assertTrue(response.isSuccessful())
            assertEquals(response.value, SUCCESS_RESPONSE.serializableData)
        }

    @Test
    fun givenAValidReportSome_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                SUCCESS_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion =
                {
                    assertPost()
                    assertJson()
                    assertQueryDoesNotExist("ignore_missing")
                    assertQueryDoesNotExist("report_missing")
                    assertPathEqual(SEND_MESSAGE_PATH)
                }
            )
            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.ReportSome(TEST_USER_LIST)
            )
            assertTrue(response.isSuccessful())
            assertEquals(response.value, SUCCESS_RESPONSE.serializableData)
        }

    // error case
    @Test
    fun givenMissingUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(MISSING_ERROR_RESPONSE)

    @Test
    fun givenDeletedUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(DELETED_ERROR_RESPONSE)

    @Test
    fun givenRedundantUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(
        REDUNDANT_ERROR_RESPONSE
    )

    private fun errorCaseTest(errorResponse: ValidJsonProvider<SendMessageResponse.MissingDevicesResponse>) =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                errorResponse.rawJson,
                statusCode = HttpStatusCode.PreconditionFailed
            )

            val messageApi: MessageApi = MessageApiV0(networkClient, EnvelopeProtoMapperImpl())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.ReportAll
            )

            assertFalse(response.isSuccessful())
            assertTrue(response.kException is SendMessageError.MissingDeviceError)
            assertEquals(
                (response.kException as SendMessageError.MissingDeviceError).errorBody,
                errorResponse.serializableData
            )
        }

    private companion object {
        val TEST_USER_LIST = listOf("user_1", "user_2", "user_3")
        const val TEST_CONVERSATION_ID = "33d8dee6-7a55-4551-97d2-bd7a5160cd4e"
        const val SEND_MESSAGE_PATH = "/conversations/$TEST_CONVERSATION_ID/otr/messages"
        val DEFAULT_PARAMETERS_RESPONSE = SendMessageRequestJson.validDefaultParameters
        val SUCCESS_RESPONSE = SendMessageResponseJson.validMessageSentJson
        val MISSING_ERROR_RESPONSE = SendMessageResponseJson.missingUsersResponse
        val DELETED_ERROR_RESPONSE = SendMessageResponseJson.deletedUsersResponse
        val REDUNDANT_ERROR_RESPONSE = SendMessageResponseJson.redundantUsersResponse

    }
}
