/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network

import com.wire.kalium.network.api.model.ProxyCredentialsDTO
import com.wire.kalium.network.api.unbound.configuration.ServerConfigDTO
import com.wire.kalium.network.exceptions.isPlatformProxyFailure
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppleSocksProxyTransportTest {
    @Test
    fun authenticatedWebSocketUsesTheSameProxyForItsHandshakeAndFrames() = runBlocking {
        withProxy(true, webSocket = true) { server ->
            val handshake = server.acceptOne(this)
            val engine = defaultHttpEngine(
                ServerConfigDTO.ApiProxy(true, "127.0.0.1", server.port), credentials(), false, emptyMap(),
            )
            val client = HttpClient(engine) {
                install(WebSockets)
                install(HttpTimeout) { requestTimeoutMillis = 5000 }
            }
            try {
                withTimeout(5000) {
                    client.webSocket("ws://proxy-fixture.invalid/proxy-check") {
                        val message = incoming.receive()
                        assertTrue(message is Frame.Text)
                        assertEquals("proxy-ok", message.readText())
                    }
                }
                val observed = handshake.await()
                assertEquals("proxy-user" to "proxy-password", observed.credentials)
                assertEquals("proxy-fixture.invalid", observed.destination)
                assertTrue(observed.request.orEmpty().contains("Upgrade: websocket", ignoreCase = true))
            } finally {
                client.close()
                engine.close()
            }
        }
    }

    @Test
    fun authenticatedHttpUsesTheProxyAndSendsCredentialsOnTheSocksHandshake() = runBlocking {
        withProxy(true) { server ->
            val handshake = server.acceptOne(this)
            assertEquals("proxy-ok", request(server, credentials()))
            val observed = handshake.await()
            assertEquals("proxy-user" to "proxy-password", observed.credentials)
            assertEquals("proxy-fixture.invalid", observed.destination)
            assertEquals(80, observed.destinationPort)
            assertTrue(observed.request.orEmpty().startsWith("GET /proxy-check HTTP/1.1"))
            assertFalse(observed.request.orEmpty().contains("proxy-password"))
            assertFalse(observed.request.orEmpty().contains("Proxy-Authorization", ignoreCase = true))
        }
    }

    @Test
    fun rejectedCredentialsFailClosedAndANewEngineUsesFreshCredentials() = runBlocking {
        withProxy(true) { server ->
            // First populate any transport caches; another account must still supply its own credentials.
            val originalHandshake = server.acceptOne(this)
            assertEquals("proxy-ok", request(server, credentials()))
            assertEquals("proxy-user" to "proxy-password", originalHandshake.await().credentials)

            val rejectedHandshake = server.acceptOne(this)
            val failure = runCatching { request(server, credentials("wrong-password")) }.exceptionOrNull()
            assertTrue(failure?.isPlatformProxyFailure() == true, "Expected SOCKS auth rejection, got $failure")
            val rejected = rejectedHandshake.await()
            assertEquals("proxy-user" to "wrong-password", rejected.credentials)
            assertNull(rejected.destination)

            val successfulHandshake = server.acceptOne(this)
            assertEquals("proxy-ok", request(server, credentials()))
            assertEquals("proxy-user" to "proxy-password", successfulHandshake.await().credentials)
        }
    }

    @Test
    fun missingCredentialsCannotBypassTheRequiredProxy() = runBlocking {
        withProxy(true) { server ->
            val handshake = server.acceptOne(this)
            assertTrue(runCatching { request(server, null) }.isFailure)
            val observed = handshake.await()
            assertNull(observed.destination)
            assertNull(observed.credentials)
            assertFalse(2 in observed.offeredMethods)
        }
    }

    @Test
    fun optionalAuthenticationDoesNotSendStoredSecretsToTheProxy() = runBlocking {
        withProxy(false) { server ->
            val handshake = server.acceptOne(this)
            assertEquals("proxy-ok", request(server, credentials(), false))
            val observed = handshake.await()
            assertNull(observed.credentials)
            assertFalse(2 in observed.offeredMethods)
            assertEquals("proxy-fixture.invalid", observed.destination)
        }
    }

    private suspend fun request(
        server: LoopbackSocks5Server,
        credentials: ProxyCredentialsDTO?,
        needsAuthentication: Boolean = true,
    ): String {
        val engine = defaultHttpEngine(
            ServerConfigDTO.ApiProxy(needsAuthentication, "127.0.0.1", server.port), credentials, false, emptyMap(),
        )
        val client = HttpClient(engine) {
            install(HttpTimeout) { requestTimeoutMillis = 5000 }
        }
        return try {
            client.get("http://proxy-fixture.invalid/proxy-check").bodyAsText()
        } finally {
            client.close()
            engine.close()
        }
    }

    private suspend fun withProxy(
        requireAuthentication: Boolean,
        webSocket: Boolean = false,
        block: suspend CoroutineScope.(LoopbackSocks5Server) -> Unit,
    ) = coroutineScope {
        val server = LoopbackSocks5Server(requireAuthentication, webSocket)
        try {
            block(server)
        } finally {
            server.stop()
        }
    }

    private fun credentials(password: String = "proxy-password") = ProxyCredentialsDTO("proxy-user", password)
}
