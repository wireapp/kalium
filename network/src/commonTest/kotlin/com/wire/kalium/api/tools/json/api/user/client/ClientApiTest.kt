package com.wire.kalium.api.tools.json.api.user.client

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.tools.json.model.ErrorResponseJson
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.network.api.user.client.ClientApiImpl
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ClientApiTest : ApiTest {
    @Test
    fun givenAValidRegisterClientRequest_whenCallingTheRegisterClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val httpClient = mockAuthenticatedHttpClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_CLIENTS)
                }
            )
            val clientApi: ClientApi = ClientApiImpl(httpClient)
            val response = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
            assertTrue(response.isSuccessful())
            assertEquals(response.value, VALID_REGISTER_CLIENT_RESPONSE.serializableData)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheRegisterClientEndpoint_theCorrectExceptionIsThrown() = runTest {
        val httpClient = mockAuthenticatedHttpClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.Unauthorized
        )
        val clientApi: ClientApi = ClientApiImpl(httpClient)
        val errorResponse = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    private companion object {
        const val PATH_CLIENTS = "/clients"
        val REGISTER_CLIENT_REQUEST = RegisterClientRequestJson.valid
        val VALID_REGISTER_CLIENT_RESPONSE = ClientResponseJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
    }
}
