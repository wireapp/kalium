/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.api.util

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.ErrorResponseJson
import com.wire.kalium.mocks.responses.PreKeyJson
import com.wire.kalium.mocks.responses.QualifiedSendMessageResponseJson
import com.wire.kalium.network.api.authenticated.prekey.PreKeyDTO
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.api.model.MLSErrorResponse
import com.wire.kalium.network.api.model.QualifiedID
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.MLSError
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.FederationErrorResponseInterceptorConflictWithMissingUsers
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.request
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

internal class WrapRequestTest : ApiTest() {

    @Test
    fun givenSuccessAndUnitResponseType_whenWrappingRequest_thenShouldReturnSuccess() = runTest {
        val client = mockUnboundNetworkClient("", HttpStatusCode.OK)
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Success<*>>(result)
    }

    @Test
    fun givenJsonBodyMatchesExpectedResponseType_whenWrappingRequest_thenShouldReturnDeserializedJsonBodyByDefault() = runTest {
        val expectedBody = PreKeyJson.valid.serializableData
        val client = mockUnboundNetworkClient(PreKeyJson.valid.rawJson, HttpStatusCode.OK)
        val result = wrapRequest<PreKeyDTO> {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Success<*>>(result)
        assertEquals(expectedBody, result.value)
    }

    @Test
    fun givenCustomSuccessHandlingAndSuccessHappens_whenWrappingRequest_thenShouldReturnCustomHandlingResult() = runTest {
        val expectedResult = NetworkResponse.Success(
            PreKeyDTO(-42, "this is a custom override for tests"), emptyMap(), HttpStatusCode.OK.value
        )
        val client = mockUnboundNetworkClient("", HttpStatusCode.OK)
        val result = wrapRequest<PreKeyDTO>(successHandler = { expectedResult }) {
            client.httpClient.request()
        }

        assertEquals(expectedResult, result)
    }

    @Test
    fun givenCustomSuccessHandlingTransformsIntoFailure_whenWrappingRequest_thenShouldReturnFailureTransformationResult() = runTest {
        val expectedResult = NetworkResponse.Error(KaliumException.GenericError(Exception("Test exception!")))
        val client = mockUnboundNetworkClient("", HttpStatusCode.OK)
        val result = wrapRequest<PreKeyDTO>(successHandler = { expectedResult }) {
            client.httpClient.request()
        }

        assertEquals(expectedResult, result)
    }

    @Test
    fun given401_whenWrappingRequest_thenShouldReturnUnauthorized() = runTest {
        val client = mockUnboundNetworkClient("", HttpStatusCode.Unauthorized)
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Error>(result)
        assertIs<KaliumException.Unauthorized>(result.kException)
    }


    @Test
    fun given401AndCustomErrorHandling_whenWrappingRequest_thenShouldReturnUnauthorized() = runTest {
        val expectedError = NetworkResponse.Error(
            ProteusClientsChangedError(
                errorBody = QualifiedSendMessageResponseJson.missingUsersResponse.serializableData
            )
        )
        val client = mockUnboundNetworkClient("", HttpStatusCode.Unauthorized)
        val result = wrapRequest<Any>(customErrorInterceptor = { expectedError }) {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Error>(result)
        assertIs<KaliumException.Unauthorized>(result.kException)
    }

    @Test
    fun givenServerErrorWithGenericAPIErrorBody_whenWrappingRequest_thenShouldReturnGenericError() = runTest {
        val expectedError = ErrorResponseJson.valid.serializableData
        val client = mockUnboundNetworkClient(ErrorResponseJson.valid.rawJson, HttpStatusCode.InternalServerError)
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Error>(result)
        assertIs<KaliumException.ServerError>(result.kException)
        assertEquals(expectedError, result.kException.errorResponse)
    }

    @Test
    fun givenBadRequestWithGenericAPIErrorBody_whenWrappingRequest_thenShouldReturnGenericError() = runTest {
        val expectedError = ErrorResponseJson.valid.serializableData
        val client = mockUnboundNetworkClient(ErrorResponseJson.valid.rawJson, HttpStatusCode.BadRequest)
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }

        assertIs<NetworkResponse.Error>(result)
        result.kException.printStackTrace()
        assertIs<KaliumException.InvalidRequestError>(result.kException)
        assertEquals(expectedError, result.kException.errorResponse)
    }

    @Test
    fun givenFailureWithMlsErrorResponseBody_whenWrappingRequest_thenShouldReturnMlsError() = runTest {
        val errorBody = MLSErrorResponse.WelcomeMismatch("Oh noes!")
        val expectedError = NetworkResponse.Error(MLSError(errorBody))
        val responseBody = KtxSerializer.json.encodeToString(MLSErrorResponse.serializer(), errorBody)
        val client = mockUnboundNetworkClient(
            responseBody = responseBody,
            statusCode = HttpStatusCode.UnprocessableEntity
        )
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }
        assertEquals(expectedError, result)
    }

    @Test
    fun given409FederationError_whenWrappingRequest_thenShouldReturnFederationError() = runTest {
        val expectedError = FederationErrorResponse.Conflict(listOf("a", "b", "c"))
        val client =
            mockUnboundNetworkClient(ErrorResponseJson.validFederationConflictingBackends(expectedError).rawJson, HttpStatusCode.Conflict)
        val result = wrapRequest<Unit> {
            client.httpClient.request()
        }
        assertIs<NetworkResponse.Error>(result)
        assertIs<FederationError>(result.kException)
        assertEquals(expectedError, result.kException.errorResponse)
    }


    @Test
    fun given409FederationError_whenWrappingRequest_thenShouldReturnFederationError2() = runTest {
        val expectedError = FederationErrorResponse.ConflictWithMissingUsers(listOf(QualifiedID("id", "domain")))
        val client =
            mockUnboundNetworkClient(ErrorResponseJson.validFederationConflictingBackendsWithMissingUsers(expectedError).rawJson, HttpStatusCode.Conflict)
        val result = wrapRequest<Unit>(
            federationErrorResponseInterceptor = FederationErrorResponseInterceptorConflictWithMissingUsers
        ) {
            client.httpClient.request()
        }
        assertIs<NetworkResponse.Error>(result)
        assertIs<FederationError>(result.kException)
        assertEquals(expectedError, result.kException.errorResponse)
    }

    @Test
    fun givenCustomErrorHandlingAndNoFederationOrUnauthorized_whenWrappingRequest_thenShouldReturnCustomError() = runTest {
        val expectedError = NetworkResponse.Error(
            ProteusClientsChangedError(
                errorBody = QualifiedSendMessageResponseJson.missingUsersResponse.serializableData
            )
        )
        val client = mockUnboundNetworkClient("", HttpStatusCode.UnprocessableEntity)
        val result = wrapRequest<Any>(customErrorInterceptor = { expectedError }) {
            client.httpClient.request()
        }
        assertIs<NetworkResponse.Error>(result)
        assertEquals(expectedError, result)
    }

    @Test
    fun givenCustomErrorHandlingTransformsIntoSuccess_whenWrappingRequest_thenShouldReturnSuccess() = runTest {
        val expectedSuccess = NetworkResponse.Success("Test string for test purposes", emptyMap(), HttpStatusCode.OK.value)
        val client = mockUnboundNetworkClient("", HttpStatusCode.UnprocessableEntity)
        val result = wrapRequest<String>(customErrorInterceptor = { expectedSuccess }) {
            client.httpClient.request()
        }
        assertIs<NetworkResponse.Success<String>>(result)
        assertEquals(expectedSuccess, result)
    }

    @Test
    fun givenCustomErrorHandling_whenWrappingRequest_thenShouldPassActualRequestDataToHandler() = runTest {
        var wasHandlerCalled = false
        val originalResponseBody = """{"TEST": "OK"}"""
        val originalResponseStatusCode = HttpStatusCode.UnprocessableEntity
        val originalResponseHeaders = mapOf("a" to "b", "c" to "d")
        val expectedSuccess = NetworkResponse.Success(JsonObject(emptyMap()), emptyMap(), HttpStatusCode.OK.value)
        val client = mockUnboundNetworkClient(originalResponseBody, originalResponseStatusCode, headers = originalResponseHeaders)
        wrapRequest<JsonObject>(customErrorInterceptor = {
            assertEquals(originalResponseBody, it.body)
            assertEquals(originalResponseStatusCode, it.status)
            assertEquals(originalResponseHeaders, it.headers)
            wasHandlerCalled = true
            expectedSuccess
        }) {
            client.httpClient.request()
        }
        assertTrue(wasHandlerCalled)
    }
}
