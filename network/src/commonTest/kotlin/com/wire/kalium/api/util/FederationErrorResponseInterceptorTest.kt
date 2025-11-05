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
import com.wire.kalium.network.api.model.Cause
import com.wire.kalium.network.api.model.FederationErrorResponse
import com.wire.kalium.network.exceptions.FederationError
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.network.utils.FederationErrorResponseInterceptor
import com.wire.kalium.network.utils.FederationErrorResponseInterceptor.UnreachableRemoteBackends
import com.wire.kalium.network.utils.HttpResponseData
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class FederationErrorResponseInterceptorTest {

    private val subject = FederationErrorResponseInterceptor

    @Test
    fun givenNonFederationIssues_whenIntercepting_thenShouldReturnNull() = runTest {
        val responseData = HttpResponseData(emptyMap(), HttpStatusCode.BadRequest, "", KtxSerializer.json)
        val result = subject.intercept(responseData)
        assertNull(result)
    }

    @Test
    fun given409StatusCode_whenIntercepting_thenShouldReturnFederationConflict() = runTest {
        val expectation = FederationErrorResponse.Conflict(listOf("b1", "c2", "a3"))
        val body = ErrorResponseJson.validFederationConflictingBackends(expectation).rawJson
        val responseData =
            HttpResponseData(emptyMap(), HttpStatusCode.Conflict, body, KtxSerializer.json)
        val result = subject.intercept(responseData)

        assertNotNull(result)
        assertIs<FederationError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

    @Test
    fun given533StatusCode_whenIntercepting_thenShouldReturnUnreachableRemoteBackends() = runTest {
        val expectation = FederationErrorResponse.Unreachable(listOf("A1", "B2", "C3"))
        val body = ErrorResponseJson.validFederationUnreachableBackends(expectation).rawJson
        val responseData =
            HttpResponseData(emptyMap(), HttpStatusCode.UnreachableRemoteBackends, body, KtxSerializer.json)
        val result = subject.intercept(responseData)

        assertNotNull(result)
        assertIs<FederationError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

    @Test
    fun givenGenericErrorWithFederationInItsLabel_whenIntercepting_thenShouldReturnFederationError() = runTest {
        val expectation = FederationErrorResponse.Generic(
            code = 42,
            message = "something went wrong",
            label = "something-federation-something",
            cause = Cause(
                type = "NOT-The-Fed-Type",
                domains = listOf("A", "B"),
                path = "hey"
            )
        )
        val responseData = HttpResponseData(
            emptyMap(),
            HttpStatusCode.Conflict,
            ErrorResponseJson.validFederationGeneric(expectation).rawJson,
            KtxSerializer.json
        )
        val result = subject.intercept(responseData)

        assertNotNull(result)
        assertIs<FederationError>(result.kException)
        assertIs<FederationErrorResponse.Generic>(result.kException.errorResponse)
    }

    @Test
    fun givenGenericErrorWithDataAndFederationType_whenIntercepting_thenShouldReturnFederationError() = runTest {
        val expectation = FederationErrorResponse.Generic(
            code = 42,
            message = "something went wrong",
            label = "something-else!!!!!",
            cause = Cause(
                type = "federation",
                domains = listOf("A", "B"),
                path = "hey"
            )
        )
        val responseData = HttpResponseData(
            emptyMap(),
            HttpStatusCode.Conflict,
            ErrorResponseJson.validFederationGeneric(expectation).rawJson,
            KtxSerializer.json
        )
        val result = subject.intercept(responseData)

        assertNotNull(result)
        assertIs<FederationError>(result.kException)
        assertEquals(expectation, result.kException.errorResponse)
    }

}
