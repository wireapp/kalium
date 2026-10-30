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
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readFully
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApplePreviewHttpEngineTest {
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
    fun givenUnreachableIpv6Answer_whenRequesting_thenTriesApprovedIpv4Answer() = runTest {
        val attempted = mutableListOf<String>()
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nWire"))
        val engine = ApplePreviewHttpEngine(
            resolveAddresses = { listOf("2606:4700:4700::1111", "93.184.215.14") },
            connect = { _, address, _ ->
                attempted += address
                if (':' in address) error("Route unavailable")
                connection
            }
        )

        HttpClient(engine).use { client ->
            assertEquals("Wire", client.get("https://example.com/").bodyAsText())
        }
        assertEquals(listOf("2606:4700:4700::1111", "93.184.215.14"), attempted)
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
    fun givenChunkedResponse_whenReading_thenUsesKtorBodyFraming() = runTest {
        val connection = FakePreviewConnection(
            listOf("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n", "4\r\nWire\r\n", "0\r\n\r\n")
        )
        HttpClient(ApplePreviewHttpEngine({ listOf("93.184.215.14") }) { _, _, _ -> connection }).use { client ->
            assertEquals("Wire", client.get("https://example.com/").bodyAsText())
        }
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
    fun givenInformationalResponse_whenReadingHeaders_thenReturnsFinalResponseAndBodyPrefix() = runTest {
        val connection = FakePreviewConnection(
            listOf("HTTP/1.1 103 Early Hints\r\nLink: </style.css>\r\n\r\nHTTP/1.1 200 OK\r\nContent-Length: 4\r\n\r\nWire")
        )

        val head = readPreviewResponseHeaders(connection)

        assertEquals(200, head.status.value)
        assertEquals("Wire", head.remainingBytes.decodeToString())
    }

    @Test
    fun givenOversizedHeaders_whenReading_thenRejectsBeforeParsingUnboundedInput() = runTest {
        val connection = FakePreviewConnection(listOf("HTTP/1.1 200 OK\r\nX-Large: ${"a".repeat(64 * 1024)}\r\n\r\n"))

        assertFailsWith<IllegalStateException> { readPreviewResponseHeaders(connection) }
    }

    @Test
    fun givenHeaderReadCancelled_whenRequesting_thenClosesConnection() = runTest {
        val reading = CompletableDeferred<Unit>()
        var closed = false
        val connection = object : PreviewAppleConnection {
            override suspend fun write(bytes: ByteArray) = Unit
            override suspend fun read(): ByteArray? {
                reading.complete(Unit)
                awaitCancellation()
            }
            override fun close() {
                closed = true
            }
        }
        HttpClient(ApplePreviewHttpEngine({ listOf("93.184.215.14") }) { _, _, _ -> connection }).use { client ->
            val request = async { client.get("https://example.com/") }
            reading.await()

            request.cancelAndJoin()

            assertTrue(closed)
        }
    }

    @Test
    fun givenPartiallyConsumedBody_whenCancelled_thenClosesConnectionAndPendingRead() = runTest {
        val reading = CompletableDeferred<Unit>()
        val consumed = CompletableDeferred<Unit>()
        var closed = false
        var firstRead = true
        val connection = object : PreviewAppleConnection {
            override suspend fun write(bytes: ByteArray) = Unit
            override suspend fun read(): ByteArray? {
                if (firstRead) {
                    firstRead = false
                    return "HTTP/1.1 200 OK\r\nContent-Length: 1000000\r\n\r\nWire".encodeToByteArray()
                }
                reading.complete(Unit)
                awaitCancellation()
            }
            override fun close() {
                closed = true
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

            assertTrue(closed)
        }
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
