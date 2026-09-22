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

package com.wire.kalium.network

import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.network.utils.installWireDefaultRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.URLProtocol
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WireDefaultRequestTest {
    @Test
    fun configuredHTTPSPortSurvivesDefaultRequestConstruction() = runTest {
        for ((endpoint, expectedPort) in listOf("https://localhost:18443/" to 18443, "https://localhost/" to 443, "http://localhost/" to 443)) {
            val engine = MockEngine { request ->
                assertEquals(URLProtocol.HTTPS, request.url.protocol)
                assertEquals(expectedPort, request.url.port)
                respond("{}")
            }
            val client = HttpClient(engine) {
                installWireDefaultRequest(TEST_BACKEND_CONFIG.copy(links = TEST_BACKEND_CONFIG.links.copy(api = endpoint)))
            }
            try {
                client.get("notifications")
            } finally {
                client.close()
                engine.close()
            }
        }
    }
}
