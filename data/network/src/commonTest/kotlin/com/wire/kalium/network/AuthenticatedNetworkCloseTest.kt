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

import com.wire.kalium.api.ApiTest
import com.wire.kalium.api.TEST_BACKEND_CONFIG
import com.wire.kalium.network.api.v0.authenticated.AccessTokenApiV0
import com.wire.kalium.network.networkContainer.AuthenticatedHttpClientProviderImpl
import com.wire.kalium.network.utils.MockWebSocketSession
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class AuthenticatedNetworkCloseTest : ApiTest() {
    @Test
    fun closeWaitsForInitializedClientsAndClosesOwnedEngine() = runTest {
        val engine = MockEngine { error("No request expected") }
        val provider = provider(engine)
        val main = provider.networkClient.httpClient
        val uncompressed = provider.networkClientWithoutCompression.httpClient
        provider.close()
        assertTrue(main.coroutineContext.job.isCompleted)
        assertTrue(uncompressed.coroutineContext.job.isCompleted)
        assertTrue(engine.coroutineContext.job.isCompleted)
        provider.close()
    }

    @Test
    fun closeDoesNotInstantiateUnusedClients() = runTest {
        val engine = MockEngine { error("No request expected") }
        val provider = provider(engine)
        assertEquals(0, engine.coroutineContext.job.children.count())
        provider.close()
        assertTrue(engine.coroutineContext.job.isCompleted)
    }

    @Test
    fun sharedInjectedEngineRemainsUsableAfterContainerClose() = runTest {
        val engine = MockEngine { error("No request expected") }
        val first = provider(engine, ownsEngine = false)
        val second = provider(engine, ownsEngine = false)
        val secondClient = second.networkClient.httpClient
        first.networkClient.httpClient
        first.close()
        assertTrue(engine.coroutineContext.job.isActive)
        assertTrue(secondClient.coroutineContext.job.isActive)
        second.close()
        engine.close()
        engine.coroutineContext.job.join()
    }

    @Test
    fun callerCancellationStillAwaitsClientCleanup() = runTest {
        val engine = MockEngine { error("No request expected") }
        val provider = provider(engine)
        val child = Job(provider.networkClient.httpClient.coroutineContext.job)
        val close = launch { provider.close() }
        runCurrent()
        close.cancel()
        runCurrent()
        assertFalse(close.isCompleted)
        child.complete()
        close.join()
        assertTrue(engine.coroutineContext.job.isCompleted)
    }

    @Test
    fun completedWebsocketClosesItsDisposableClient() = runTest {
        val engine = MockEngine { error("No request expected") }
        val sessionJob = Job()
        lateinit var disposable: HttpClient
        val websocket = AuthenticatedWebSocketClient(engine, TEST_BEARER_AUTH_PROVIDER, TEST_BACKEND_CONFIG, kaliumLogger) { client, _ ->
            disposable = client
            MockWebSocketSession(sessionJob)
        }
        websocket.createWebSocketSession("client") {}
        sessionJob.complete()
        websocket.close()
        assertTrue(disposable.coroutineContext.job.isCompleted)
        engine.close()
        engine.coroutineContext.job.join()
    }

    @Test
    fun failedHandshakeClosesClientAndLeavesSharedEngineActive() = runTest {
        val engine = MockEngine { error("No request expected") }
        lateinit var disposable: HttpClient
        val websocket = AuthenticatedWebSocketClient(engine, TEST_BEARER_AUTH_PROVIDER, TEST_BACKEND_CONFIG, kaliumLogger) { client, _ ->
            disposable = client
            error("Handshake failed")
        }
        assertFailsWith<IllegalStateException> { websocket.createWebSocketSession("client") {} }
        assertTrue(disposable.coroutineContext.job.isCompleted)
        assertTrue(engine.coroutineContext.job.isActive)
        websocket.close()
        engine.close()
        engine.coroutineContext.job.join()
    }

    @Test
    fun cancelledRealHandshakeDoesNotKeepDisposableClientAlive() = runTest {
        val entered = CompletableDeferred<Unit>()
        val engine = MockEngine {
            entered.complete(Unit)
            awaitCancellation()
        }
        val websocket = AuthenticatedWebSocketClient(engine, TEST_BEARER_AUTH_PROVIDER, TEST_BACKEND_CONFIG, kaliumLogger)
        val attempt = launch { websocket.createWebSocketSession("client") { url.host = "example.com" } }
        entered.await()
        attempt.cancelAndJoin()
        websocket.close()
        engine.close()
        engine.coroutineContext.job.join()
        assertTrue(engine.coroutineContext.job.isCompleted)
    }

    private fun provider(engine: HttpClientEngine, ownsEngine: Boolean = true) = AuthenticatedHttpClientProviderImpl(
        sessionManager = TEST_SESSION_MANAGER,
        nomadServiceUrl = null,
        accessTokenApi = { AccessTokenApiV0(it) },
        engine = engine,
        ownsEngine = ownsEngine,
        webSocketSessionProvider = null,
        kaliumLogger = kaliumLogger,
    )
}
