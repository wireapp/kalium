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

package com.wire.kalium.api.v0.user.client

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.json.model.ErrorResponseJson
import com.wire.kalium.mocks.responses.ClientResponseJson
import com.wire.kalium.mocks.responses.RegisterClientRequestJson
import com.wire.kalium.mocks.responses.RegisterTokenJson
import com.wire.kalium.mocks.responses.UpdateClientRequestJson
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.client.ClientCapabilityDTO
import com.wire.kalium.network.api.base.authenticated.client.UpdateClientCapabilitiesRequest
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
internal class ClientApiV0Test : ApiTest() {
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
    fun givenAValidUpdateClientMlsPublicKeysRequest_whenCallingTheUpdateClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
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

            val response = clientApi.updateClientMlsPublicKeys(UPDATE_CLIENT_REQUEST.serializableData, VALID_CLIENT_ID)

            assertTrue(response.isSuccessful())
        }
    @Test
    fun givenAValidUpdateClientCapabilitiesRequest_whenCallingTheUpdateClientEndpoint_theRequestShouldBeConfiguredCorrectly() =
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

            val response = clientApi.updateClientCapabilities(
                UpdateClientCapabilitiesRequest(listOf(ClientCapabilityDTO.LegalHoldImplicitConsent)),
                VALID_CLIENT_ID
            )

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
