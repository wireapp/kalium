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
package com.wire.kalium.cells.domain

import com.wire.kalium.cells.data.CellsApiImpl
import com.wire.kalium.cells.sdk.kmp.api.NodeServiceApi
import com.wire.kalium.cells.sdk.kmp.model.RestCheckResult
import com.wire.kalium.cells.sdk.kmp.model.RestCreateCheckResponse
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test

class CellsApiTest {

    @Test
    fun given_new_file_pre_check_then_success_is_returned() = runTest {

        val (_, cellApi) = Arrangement()
            .withHttpClient(
                Json.encodeToString(
                    RestCreateCheckResponse(
                        results = listOf(RestCheckResult(exists = false))
                    )
                ).toByteArray()
            )
            .arrange()

        val result = cellApi.preCheck("path")

        // TODO: Fix issue with response deserialization
//           assertEquals(PreCheckResultDTO(fileExists = false), result)
    }

    @Test
    fun given_existing_file_pre_check_then_existing_file_is_returned() = runTest {

    }

    @Test
    fun given_pre_check_failure_then_failure_is_returned() = runTest {

    }

    private class Arrangement() {

        var httpClient: HttpClient = HttpClient(createMockEngine(
            ByteArray(0),
            HttpStatusCode.OK
        ))

        val nodeService = NodeServiceApi("", httpClient)

        fun withHttpClient(body: ByteArray, statusCode: HttpStatusCode = HttpStatusCode.OK) = apply {
            httpClient = HttpClient(createMockEngine(body, statusCode)) {
                install(ContentNegotiation) {
                    json()
                }
            }
        }

        fun arrange() = this to CellsApiImpl(nodeService)
    }
}

private fun createMockEngine(
    responseBody: ByteArray,
    statusCode: HttpStatusCode,
    assertion: (HttpRequestData.() -> Unit) = {},
    headers: Map<String, String>? = null
): MockEngine {
    val newHeaders: Map<String, List<String>> = (headers?.let {
        headers.mapValues { listOf(it.value) }
    } ?: run {
        mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
    })

    return MockEngine { request ->
        request.assertion()
        respond(
            content = responseBody,
            status = statusCode,
            headers = HeadersImpl(newHeaders)
        )
    }
}
