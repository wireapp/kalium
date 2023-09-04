/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import TestNetworkStateObserver
import com.wire.kalium.network.NetworkStateObserver
import com.wire.kalium.network.UnboundNetworkClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HeadersImpl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel

/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

    /**
     * Creates a mock Ktor Http client
     * @param responseBody the response body as a ByteArray
     * @param statusCode the response http status code
     * @param assertion lambda function to apply assertions to the request
     * @return mock Ktor http client
     */
    fun mockUnboundNetworkClient(
        responseBody: String,
        statusCode: HttpStatusCode,
        assertion: (HttpRequestData.() -> Unit) = {},
        headers: Map<String, String>? = null,

        networkStateObserver: NetworkStateObserver = TestNetworkStateObserver.DEFAULT_TEST_NETWORK_STATE_OBSERVER,
    ): UnboundNetworkClient {
        val mockEngine = createMockEngine(
            ByteReadChannel(responseBody),
            statusCode,
            assertion,
            headers
        )
        return UnboundNetworkClient(
            engine = mockEngine,
            networkStateObserver = networkStateObserver
        )
    }

    private fun createMockEngine(
        responseBody: ByteReadChannel,
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
}
