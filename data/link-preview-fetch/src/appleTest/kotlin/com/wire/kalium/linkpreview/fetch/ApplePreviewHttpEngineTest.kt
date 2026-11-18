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
package com.wire.kalium.linkpreview.fetch

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.sizeOf
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import platform.Network.NW_PARAMETERS_DEFAULT_CONFIGURATION
import platform.Network.nw_connection_create
import platform.Network.nw_connection_set_queue
import platform.Network.nw_connection_start
import platform.Network.nw_endpoint_create_host
import platform.Network.nw_parameters_create_secure_tcp
import platform.darwin.dispatch_queue_create
import platform.posix.AF_INET
import platform.posix.MSG_NOSIGNAL
import platform.posix.SOCK_STREAM
import platform.posix.SOL_SOCKET
import platform.posix.SO_RCVTIMEO
import platform.posix.accept
import platform.posix.bind
import platform.posix.close
import platform.posix.getsockname
import platform.posix.listen
import platform.posix.recv
import platform.posix.send
import platform.posix.setsockopt
import platform.posix.sockaddr_in
import platform.posix.socket
import platform.posix.socklen_tVar
import platform.posix.timeval
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ApplePreviewHttpEngineTest {
    @Test
    fun givenNativeTlsFailure_whenReading_thenReportsFailureWithoutCrashing(): Unit = runBlocking {
        val listener = socket(AF_INET, SOCK_STREAM, 0)
        check(listener >= 0)
        try {
            val port = listenOnLoopback(listener)
            withTimeout(5_000) {
                val clientFinished = CompletableDeferred<Unit>()
                val rejectHandshake = async(Dispatchers.Default) {
                    val peer = accept(listener, null, null)
                    check(peer >= 0) { "TLS fixture did not accept a connection" }
                    try {
                        setReadTimeout(peer)
                        val hello = ByteArray(16_384)
                        check(hello.usePinned { recv(peer, it.addressOf(0), hello.size.convert(), 0) } > 0)
                        // Reply to the ClientHello with a fatal TLS handshake_failure alert.
                        val alert = byteArrayOf(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x28)
                        check(alert.usePinned { send(peer, it.addressOf(0), alert.size.convert(), MSG_NOSIGNAL) } == alert.size.toLong())
                        clientFinished.await()
                    } finally {
                        close(peer)
                    }
                }

                val endpoint = nw_endpoint_create_host("127.0.0.1", port.toString())
                val parameters = nw_parameters_create_secure_tcp({ _ -> }, NW_PARAMETERS_DEFAULT_CONFIGURATION)
                val native = checkNotNull(nw_connection_create(endpoint, parameters))
                nw_connection_set_queue(native, dispatch_queue_create("preview-regression", null))
                nw_connection_start(native)
                val connection = NetworkPreviewConnection(native)
                try {
                    assertFailsWith<IllegalStateException> { connection.read() }
                } finally {
                    clientFinished.complete(Unit)
                    connection.close()
                }
                rejectHandshake.await()
            }
        } finally {
            close(listener)
        }
    }

    private fun listenOnLoopback(listener: Int): Int = memScoped {
        val address = alloc<sockaddr_in>()
        address.sin_len = sizeOf<sockaddr_in>().convert()
        address.sin_family = AF_INET.convert()
        address.sin_addr.s_addr = 0x0100007fu // 127.0.0.1 in network byte order
        address.sin_port = 0u
        val length = alloc<socklen_tVar>()
        length.value = sizeOf<sockaddr_in>().convert()
        check(bind(listener, address.ptr.reinterpret(), length.value) == 0)
        check(listen(listener, 1) == 0)
        setReadTimeout(listener)
        check(getsockname(listener, address.ptr.reinterpret(), length.ptr) == 0)
        val port = address.sin_port.toInt()
        ((port and 0xff) shl 8) or (port ushr 8)
    }

    private fun setReadTimeout(socket: Int) = memScoped {
        val timeout = alloc<timeval>()
        timeout.tv_sec = 5
        timeout.tv_usec = 0
        check(setsockopt(socket, SOL_SOCKET, SO_RCVTIMEO, timeout.ptr, sizeOf<timeval>().convert()) == 0)
    }

    @Test
    fun givenMixedDnsAnswers_whenRequesting_thenDoesNotOpenAnyConnection() = runTest {
        var connectionOpened = false
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("93.184.215.14", "0:0:0:0:0:0:0:1") },
            connect = { _, _, _ ->
                connectionOpened = true
                error("Connection must not be opened")
            }
        )

        HttpClient(engine).use { client ->
            assertFailsWith<IllegalArgumentException> { client.get("https://example.com/") }
        }
        assertFalse(connectionOpened)
    }

    @Test
    fun givenPublicAnswer_whenRequesting_thenConnectsToThatAddressWithOriginalTlsHostname() = runTest {
        val connected = mutableListOf<Triple<String, String, Int>>()
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nWire"))
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("93.184.215.14") },
            connect = { host, address, port ->
                connected += Triple(host, address, port)
                connection
            }
        )

        HttpClient(engine).use { client ->
            assertEquals("Wire", client.get("https://example.com:8443/article").bodyAsText())
        }
        assertEquals(listOf(Triple("example.com", "93.184.215.14", 8443)), connected)
        assertTrue(connection.written.startsWith("GET /article HTTP/1.1\r\nHost: example.com:8443\r\n"))
        assertTrue(connection.closed)
    }

    @Test
    fun givenRootQueryAndCredentialsInHeaders_whenRequesting_thenPreservesPathWithoutForwardingCredentials() = runTest {
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"))
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("93.184.215.14") },
            connect = { _, _, _ -> connection }
        )

        HttpClient(engine).use { client ->
            client.get("https://example.com?source=chat#fragment") {
                header(HttpHeaders.Authorization, "Bearer fixture-token")
                header(HttpHeaders.Cookie, "session=fixture-cookie")
            }.bodyAsText()
        }
        assertTrue(connection.written.startsWith("GET /?source=chat HTTP/1.1\r\n"))
        assertFalse(connection.written.contains("fixture-token"))
        assertFalse(connection.written.contains("fixture-cookie"))
        assertFalse(connection.written.contains("fragment"))
    }

    @Test
    fun givenCrLfInUrl_whenRequesting_thenRejectsRawCharactersAndPreservesEncodedCharacters() = runTest {
        var opened = 0
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"))
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("93.184.215.14") },
            connect = { _, _, _ ->
                opened++
                connection
            }
        )
        HttpClient(engine).use { client ->
            assertFailsWith<IllegalArgumentException> {
                client.get("https://example.com/article\r\nX-Injected: value")
            }
            assertEquals(0, opened)

            client.get("https://example.com/%0d%0aX-Injected:value").bodyAsText()

            assertEquals(1, opened)
            assertTrue(connection.written.startsWith("GET /%0d%0aX-Injected:value HTTP/1.1\r\n"))
        }
    }

    @Test
    fun givenOversizedHeaders_whenReading_thenRejectsBeforeParsingUnboundedInput() = runTest {
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nX-Large: ${"a".repeat(64 * 1024)}\r\n\r\n"))

        assertFailsWith<IllegalStateException> { readPreviewResponseHeaders(connection) }
    }

    @Test
    fun givenPartiallyConsumedBody_whenCancelled_thenClosesConnectionAndPendingRead() = runTest {
        val reading = CompletableDeferred<Unit>()
        val consumed = CompletableDeferred<Unit>()
        val closed = CompletableDeferred<Unit>()
        val pendingReadCancelled = CompletableDeferred<Unit>()
        var firstRead = true
        val connection = object : PreviewAppleConnection {
            override suspend fun write(bytes: ByteArray) = Unit
            override suspend fun read(): ByteArray? {
                if (firstRead) {
                    firstRead = false
                    return "HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\nWire".encodeToByteArray()
                }
                reading.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    pendingReadCancelled.complete(Unit)
                }
            }
            override fun close() {
                closed.complete(Unit)
            }
        }
        HttpClient(ApplePreviewHttpEngine({ listOf("93.184.215.14") }) { _, _, _ -> connection }).use { client ->
            val request = async {
                client.prepareGet("https://example.com/").execute { response ->
                    val prefix = ByteArray(4)
                    response.body<ByteReadChannel>().readFully(prefix)
                    assertEquals("Wire", prefix.decodeToString())
                    consumed.complete(Unit)
                    awaitCancellation()
                }
            }
            reading.await()
            consumed.await()

            request.cancelAndJoin()

            closed.await()
            pendingReadCancelled.await()
        }
    }

    @Test
    fun givenUnicodePathAndQuery_whenRequesting_thenSendsPercentEncodedAsciiTarget() = runTest {
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n"))
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("93.184.215.14") },
            connect = { _, _, _ -> connection }
        )

        HttpClient(engine).use { client -> client.get("https://example.com/caf\u00e9/%41?q=\u00fc").bodyAsText() }

        assertTrue(connection.written.startsWith("GET /caf%C3%A9/%41?q=%C3%BC HTTP/1.1\r\n"), connection.written)
    }

    @Test
    fun givenStalledConnectionOrBody_whenFetching_thenTimesOutAsFailureNotCancellation() = runBlocking {
        val stalledConnect = ApplePreviewHttpEngine({ listOf("93.184.215.14") }) { _, _, _ -> awaitCancellation() }
        val stalledBody = ApplePreviewHttpEngine({ listOf("93.184.215.14") }) { _, _, _ ->
            StalledAfterPrefixConnection("HTTP/1.1 200 OK\r\nContent-Type: image/png\r\nContent-Length: 100\r\n\r\nab")
        }

        listOf(stalledConnect, stalledBody).forEach { engine ->
            val fetcher = LinkPreviewFetcherImpl { previewTestClient(engine) }
            val result = runCatching { fetcher.fetchImage("https://example.com/image.png") }

            assertNull(result.exceptionOrNull())
            assertNull(result.getOrNull())
        }
    }

    private fun previewTestClient(engine: ApplePreviewHttpEngine) = HttpClient(engine) {
        install(HttpTimeout) {
            connectTimeoutMillis = 100
            socketTimeoutMillis = 100
        }
        followRedirects = false
        expectSuccess = false
    }

    private class StalledAfterPrefixConnection(private val prefix: String) : PreviewAppleConnection {
        private var sent = false

        override suspend fun write(bytes: ByteArray) = Unit

        override suspend fun read(): ByteArray? {
            if (!sent) {
                sent = true
                return prefix.encodeToByteArray()
            }
            awaitCancellation()
        }

        override fun close() = Unit
    }

    private class FakePreviewConnection(chunks: List<String>) : PreviewAppleConnection {
        private val chunks = ArrayDeque(chunks.map(String::encodeToByteArray))
        var written: String = ""
        var closed: Boolean = false

        override suspend fun write(bytes: ByteArray) {
            written += bytes.decodeToString()
        }

        override suspend fun read(): ByteArray? = chunks.removeFirstOrNull()

        override fun close() {
            closed = true
        }
    }
}
