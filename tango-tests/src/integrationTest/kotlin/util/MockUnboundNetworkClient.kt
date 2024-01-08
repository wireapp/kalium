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
package util

import com.wire.kalium.network.networkContainer.KaliumUserAgentProvider
import com.wire.kalium.network.tools.ApiVersionDTO
import com.wire.kalium.network.tools.ServerConfigDTO
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.fail

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

    /**
     * Creates a mock Ktor Http client
     * @param responseBody the response body as a ByteArray
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    class TestRequestHandler(
        val path: String,
        val responseBody: String,
        val statusCode: HttpStatusCode,
        val assertion: (HttpRequestData.() -> Unit) = {},
        val headers: Map<String, String>? = null,
        val httpMethod: HttpMethod? = null
    )

    fun createMockEngine(
        expectedRequests: List<TestRequestHandler>
    ): MockEngine = MockEngine { currentRequest ->
        expectedRequests.forEach { request ->
            val head: Map<String, List<String>> = (request.headers?.let {
                mutableMapOf(HttpHeaders.ContentType to "application/json").plus(request.headers).mapValues { listOf(it.value) }
            } ?: run {
                mapOf(HttpHeaders.ContentType to "application/json").mapValues { listOf(it.value) }
            })
            if (request.path == currentRequest.url.toString() && request.httpMethod == currentRequest.method) {
                return@MockEngine respond(
                    content = ByteReadChannel(request.responseBody),
                    status = request.statusCode,
                    headers = HeadersImpl(head)
                )
            }
        }
        fail("no expected response was found for ${currentRequest.method.value}:${currentRequest.url}")
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

    val TEST_BACKEND_CONFIG =
        ServerConfigDTO(
            id = "id",
            ServerConfigDTO.Links(
                "https://test.api.com",
                "https://test.account.com",
                "https://test.ws.com",
                "https://test.blacklist",
                "https://test.teams.com",
                "https://test.wire.com",
                "Test Title",
                false,
                null
            ),
            ServerConfigDTO.MetaData(
                false,
                ApiVersionDTO.Valid(1),
                null
            )
        )

    val TEST_BACKEND_LINKS =
        ServerConfigDTO.Links(
            "https://test.api.com",
            "https://test.account.com",
            "https://test.ws.com",
            "https://test.blacklist",
            "https://test.teams.com",
            "https://test.wire.com",
            "Test Title",
            false,
            null
        )

    val TEST_BACKEND =
        ServerConfigDTO(
            id = "id",
            links = TEST_BACKEND_LINKS,
            metaData = ServerConfigDTO.MetaData(
                false,
                ApiVersionDTO.Valid(0),
                domain = null
            )
        )
}
