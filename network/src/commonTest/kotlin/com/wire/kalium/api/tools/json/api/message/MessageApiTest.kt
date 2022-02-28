package com.wire.kalium.api.tools.json.api.message

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessageApiImp
import com.wire.kalium.network.api.message.SendMessageResponse
import com.wire.kalium.network.api.message.provideEnvelopeProtoMapper
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
class MessageApiTest : ApiTest {
    // valid
    @Test
    fun givenAValidIgnoreAlloption_whenSendingAMessage_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
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
            val messageApi: MessageApi = MessageApiImp(httpClient, provideEnvelopeProtoMapper())
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
            val httpClient = mockAuthenticatedHttpClient(
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
            val messageApi: MessageApi = MessageApiImp(httpClient, provideEnvelopeProtoMapper())
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
            val httpClient = mockAuthenticatedHttpClient(
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
            val messageApi: MessageApi = MessageApiImp(httpClient, provideEnvelopeProtoMapper())
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
            val httpClient = mockAuthenticatedHttpClient(
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
            val messageApi: MessageApi = MessageApiImp(httpClient, provideEnvelopeProtoMapper())
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
    fun givenRedundantUsersError_whenSendingAMessage_TheCorrectErrorIsPropagate() = errorCaseTest(REDUNDANT_ERROR_RESPONSE)

    private fun errorCaseTest(errorResponse: ValidJsonProvider<SendMessageResponse.MissingDevicesResponse>) =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                errorResponse.rawJson,
                statusCode = HttpStatusCode.PreconditionFailed
            )

            val messageApi: MessageApi = MessageApiImp(httpClient, provideEnvelopeProtoMapper())
            val response = messageApi.sendMessage(
                DEFAULT_PARAMETERS_RESPONSE.serializableData,
                TEST_CONVERSATION_ID,
                MessageApi.MessageOption.ReportAll
            )

            assertFalse(response.isSuccessful())
            assertTrue(response.kException is SendMessageError.MissingDeviceError)
            assertEquals((response.kException as SendMessageError.MissingDeviceError).errorBody, errorResponse.serializableData)
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
