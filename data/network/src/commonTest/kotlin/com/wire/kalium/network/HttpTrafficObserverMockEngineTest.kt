/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
import com.wire.kalium.api.TestSessionManagerV0
import com.wire.kalium.network.api.model.UserId
import com.wire.kalium.network.api.unbound.configuration.ApiVersionDTO
import com.wire.kalium.network.networkContainer.AuthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkClientProvider
import com.wire.kalium.network.networkContainer.UnauthenticatedNetworkContainer
import com.wire.kalium.network.networkContainer.UnboundNetworkContainerCommon
import com.wire.kalium.network.session.SessionManager
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HttpTrafficObserverMockEngineTest {
    @Test
    fun injectedEngineTakesPriorityOverObserverForEveryVersion() = runTest {
        val observer = object : HttpTrafficObserver {
            override fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?) =
                error("Mock requests must not be observed")
            override fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?) =
                error("Mock responses must not be observed")
        }
        val engine = MockEngine { respond("mock response") }
        try {
            for (version in 0..17) {
                val config = TEST_BACKEND_CONFIG.copy(metaData = TEST_BACKEND_CONFIG.metaData.copy(commonApiVersion = ApiVersionDTO.Valid(version)))
                val manager = object : SessionManager by TestSessionManagerV0() {
                    override fun serverConfig() = config
                }
                val authenticated = AuthenticatedNetworkContainer.create(
                    manager, selfUserId = UserId("self", "wire.com"), userAgent = "test",
                    certificatePinning = emptyMap(), mockEngine = engine, mockWebSocketSession = null,
                    kaliumLogger = kaliumLogger, httpTrafficObserver = observer,
                )
                try {
                    assertSame(engine, authenticated.cellsHttpClient.engine)
                    assertEquals("mock response", authenticated.cellsHttpClient.get("test").bodyAsText())
                } finally {
                    authenticated.close()
                }
                assertTrue(engine.coroutineContext.job.isActive)
                val unauthenticated = UnauthenticatedNetworkContainer.create(config, null, "test", false, emptyMap(), engine, observer)
                val client = (unauthenticated as UnauthenticatedNetworkClientProvider).unauthenticatedNetworkClient.httpClient
                try {
                    assertSame(engine, client.engine)
                    assertEquals("mock response", client.get("test").bodyAsText())
                } finally {
                    client.close()
                }
            }
            val unbound = UnboundNetworkContainerCommon("test", false, emptyMap(), engine, false, observer)
            try {
                assertSame(engine, unbound.cellsClient.engine)
                assertEquals("mock response", unbound.cellsClient.get("https://wire.com/test").bodyAsText())
            } finally {
                unbound.cellsClient.close()
            }
            assertTrue(engine.coroutineContext.job.isActive)
        } finally {
            engine.close()
            engine.coroutineContext.job.join()
        }
    }
}
