package com.wire.kalium.api.v0.user.client

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.model.ClientResponseJson
import com.wire.kalium.model.RegisterClientRequestJson
import com.wire.kalium.model.RegisterTokenJson
import com.wire.kalium.model.UpdateClientRequestJson
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.v0.authenticated.ClientApiV0
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.isSuccessful
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@ExperimentalCoroutinesApi
class ClientApiV0Test : ApiTest() {
    @Test
    fun givenAValidRegisterClientRequest_whenCallingTheRegisterClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                VALID_REGISTER_CLIENT_RESPONSE.rawJson,
                statusCode = HttpStatusCode.Created,
                assertion = {
                    assertPost()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual(PATH_CLIENTS)
                }
            )
            val clientApi: ClientApi = ClientApiV0(networkClient)
            val response = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
            assertTrue(response.isSuccessful())
            assertEquals(response.value, VALID_REGISTER_CLIENT_RESPONSE.serializableData)
        }

    @Test
    fun givenTheServerReturnsAnError_whenCallingTheRegisterClientEndpoint_thenExceptionIsPropagated() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            ErrorResponseJson.valid.rawJson,
            statusCode = HttpStatusCode.BadRequest
        )
        val clientApi: ClientApi = ClientApiV0(networkClient)
        val errorResponse = clientApi.registerClient(REGISTER_CLIENT_REQUEST.serializableData)
        assertFalse(errorResponse.isSuccessful())
        assertTrue(errorResponse.kException is KaliumException.InvalidRequestError)
        assertEquals((errorResponse.kException as KaliumException.InvalidRequestError).errorResponse, ERROR_RESPONSE)
    }

    @Test
    fun givenAValidUpdateClientRequest_whenCallingTheUpdateClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val networkClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertPut()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual("$PATH_CLIENTS/$VALID_CLIENT_ID")
                }
            )
            val clientApi: ClientApi = ClientApiV0(networkClient)
            val response = clientApi.updateClient(UPDATE_CLIENT_REQUEST.serializableData, VALID_CLIENT_ID)
            assertTrue(response.isSuccessful())
        }

    @Test
    fun givenAValidDeleteClientRequest_whenCallingDeleteClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
        runTest {
            val password = "password"
            val httpClient = mockAuthenticatedNetworkClient(
                "",
                statusCode = HttpStatusCode.OK,
                assertion = {
                    assertDelete()
                    assertJson()
                    assertNoQueryParams()
                    assertPathEqual("$PATH_CLIENTS/$VALID_CLIENT_ID")
                }
            )
            val clientApi: ClientApi = ClientApiV0(httpClient)
            val response = clientApi.deleteClient(password, VALID_CLIENT_ID)
            assertTrue(response.isSuccessful())
        }

    @Test
    fun givenAValidRequest_whenRegisteredValidToken_theTokenRegisteredSuccessfully() = runTest {
        val networkClient = mockAuthenticatedNetworkClient(
            RegisterTokenJson.registerTokenResponse, statusCode = HttpStatusCode.Created, assertion = {
                assertPost()
                assertJsonBodyContent(VALID_PUSH_TOKEN_REQUEST.rawJson)
            }
        )
        val clientApi = ClientApiV0(networkClient)
        clientApi.registerToken(VALID_PUSH_TOKEN_REQUEST.serializableData)

        val actual = clientApi.registerToken(VALID_PUSH_TOKEN_REQUEST.serializableData)
        assertIs<NetworkResponse.Success<Unit>>(actual)
        assertTrue(actual.isSuccessful())
    }

    @Test
    fun whenUnregisteringToken_theRequestIsConfiguredCorrectly() = runTest {
        val pid = "token_id"
        val networkClient = mockAuthenticatedNetworkClient(
            "", statusCode = HttpStatusCode.Created, assertion = {
                assertDelete()
                assertPathEqual("/push/tokens/$pid")
            }
        )
        val clientApi = ClientApiV0(networkClient)
        val actual = clientApi.deregisterToken(pid)
        assertIs<NetworkResponse.Success<Unit>>(actual)
        assertTrue(actual.isSuccessful())
    }

    private companion object {
        const val PATH_USERS = "users"
        const val VALID_CLIENT_ID = "39s3ds2020sda"
        const val PATH_CLIENTS = "/clients"
        val REGISTER_CLIENT_REQUEST = RegisterClientRequestJson.valid
        val VALID_REGISTER_CLIENT_RESPONSE = ClientResponseJson.valid
        val UPDATE_CLIENT_REQUEST = UpdateClientRequestJson.valid
        val ERROR_RESPONSE = ErrorResponseJson.valid.serializableData
        val VALID_PUSH_TOKEN_REQUEST = RegisterTokenJson.validPushTokenRequest
    }
}
