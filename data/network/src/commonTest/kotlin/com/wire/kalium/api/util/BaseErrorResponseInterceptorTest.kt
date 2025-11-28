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

import com.wire.kalium.mocks.responses.ErrorResponseJson
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.BaseErrorResponseInterceptor
import com.wire.kalium.network.utils.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class BaseErrorResponseInterceptorTest {

    private val subject = BaseErrorResponseInterceptor

    @Test
    fun givenErrorResponse_whenIntercepting_thenShouldReturnErrorWithProperCodeMessageLabel() = runTest {
        val expectation = GenericAPIErrorResponse(
            code = HttpStatusCode.BadRequest.value,
            message = "something went wrong",
            label = "something-wrong"
        )
        val body = ErrorResponseJson.valid(expectation).rawJson
        val httpResponse = HttpResponseData(emptyMap(), HttpStatusCode.BadRequest, body, KtxSerializer.json)

        val result = subject.intercept(httpResponse)

        assertNotNull(result)
        assertIs<KaliumException.InvalidRequestError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

    @Test
    fun givenServerErrorResponse_whenIntercepting_thenShouldReturnServerError() = runTest {
        val expectation = GenericAPIErrorResponse(
            code = HttpStatusCode.InternalServerError.value,
            message = "internalServerError",
            label = "server-error"
        )
        val body = ErrorResponseJson.valid(expectation).rawJson
        val httpResponse = HttpResponseData(emptyMap(), HttpStatusCode.InternalServerError, body, KtxSerializer.json)

        val result = subject.intercept(httpResponse)

        assertNotNull(result)
        assertIs<KaliumException.ServerError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

    @Test
    fun givenRedirectResponse_whenIntercepting_thenShouldReturnRedirectError() = runTest {
        val expectation = GenericAPIErrorResponse(
            code = HttpStatusCode.MovedPermanently.value,
            message = "the princess is in another castle",
            label = "princess-is-gone"
        )
        val body = ErrorResponseJson.valid(expectation).rawJson
        val httpResponse = HttpResponseData(emptyMap(), HttpStatusCode.MovedPermanently, body, KtxSerializer.json)

        val result = subject.intercept(httpResponse)

        assertNotNull(result)
        assertIs<KaliumException.RedirectError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

    @Test
    fun givenMalformedResponse_whenIntercepting_thenShouldReturnGenericResponseErrorWithStatusAndBody() = runTest {
        val statusCode = HttpStatusCode.BadRequest
        val malformedBody = "{ invalid json }"
        val expectation = GenericAPIErrorResponse(
            code = statusCode.value,
            message = malformedBody,
            label = statusCode.description
        )
        val httpResponse = HttpResponseData(emptyMap(), statusCode, malformedBody, KtxSerializer.json)

        val result = subject.intercept(httpResponse)

        assertNotNull(result)
        assertIs<KaliumException.InvalidRequestError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

}
