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
package com.wire.kalium.network.utils

import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.utils.io.ByteReadChannel

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
object MockUnboundNetworkClient {
    init {
        KaliumUserAgentProvider.setUserAgent("test/useragent")
    }

    fun createMockEngine(
        expectedRequests: List<TestRequestHandler>
    ): MockEngine = MockEngine { currentRequest ->
        val currentPath = currentRequest.url.encodedPath
        expectedRequests.forEach { request ->
            val expectedPath = URLBuilder(request.path).build().encodedPath
            val head: Map<String, List<String>> = (request.headers?.let { headers ->
                mutableMapOf(HttpHeaders.ContentType to "application/json").plus(headers).mapValues { listOf(it.value) }
            } ?: run {
                mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
            })
            if (expectedPath == currentPath && request.httpMethod == currentRequest.method) {
                return@MockEngine respond(
                    content = ByteReadChannel(request.responseBody),
                    status = request.statusCode,
                    headers = HeadersImpl(head)
                )
            }
        }
        println("no expected response was found for ${currentRequest.method.value}:${currentRequest.url}")
        throw UnsupportedOperationException("no expected response was found for ${currentRequest.method.value}:${currentRequest.url}")
    }

    fun createMockEngine2(
        responseBody: String,
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
                content = ByteReadChannel(responseBody),
                status = statusCode,
                headers = HeadersImpl(newHeaders)
            )
        }
    }

}
