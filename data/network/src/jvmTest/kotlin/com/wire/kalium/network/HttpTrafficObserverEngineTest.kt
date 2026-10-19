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

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttpEngine
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import okhttp3.ConnectionSpec
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpTrafficObserverEngineTest {
    @Test
    fun enabledObserverCapturesOneShotUploadAndLeavesGzipResponseReadable() = runTest {
        val observer = RecordingTrafficObserver()
        val received = CompletableDeferred<Pair<String, String?>>()
        val compressed = Buffer().apply {
            GzipSink(this).buffer().use { it.writeUtf8("response payload") }
        }.readByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/private/path") { exchange ->
                received.complete(exchange.requestBody.use { it.readBytes().decodeToString() } to
                    exchange.requestHeaders.getFirst("Authorization"))
                exchange.responseHeaders.add("Content-Encoding", "gzip")
                exchange.responseHeaders.add("X-Private", "response-secret")
                exchange.sendResponseHeaders(201, compressed.size.toLong())
                exchange.responseBody.use { it.write(compressed) }
            }
            start()
        }
        val engine = engine(observer)
        val client = HttpClient(engine) { install(ContentEncoding) { gzip() } }
        var reads = 0
        val channel = ByteReadChannel("upload payload")
        val url = "http://127.0.0.1:${server.address.port}/private/path?token=secret"
        try {
            val response = client.post(url) {
                header("Authorization", "Bearer request-secret")
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentLength: Long = 14
                    override fun readFrom(): ByteReadChannel {
                        reads++
                        return channel
                    }
                })
            }
            assertEquals("response payload", response.bodyAsText())
            assertEquals(1, reads)
            assertEquals("upload payload" to "Bearer request-secret", received.await())
            assertEquals("POST", observer.requestMethod)
            assertEquals(url, observer.requestUrl)
            assertEquals(listOf("Bearer request-secret"), observer.requestHeaders["Authorization"])
            assertEquals("upload payload", observer.requestBody?.decodeToString())
            assertEquals(201, observer.responseStatus)
            assertEquals(listOf("response-secret"), observer.responseHeaders["X-private"])
            assertEquals("response payload", observer.responseBody?.decodeToString())
            assertNull(response.headers["Content-Encoding"])
            assertNull(response.headers["Content-Length"])
            assertEquals(listOf("request", "response"), observer.events)
        } finally {
            client.close()
            engine.close()
            engine.coroutineContext.job.join()
            server.stop(0)
        }
    }

    @Test
    fun earlyUploadRejectionStillReportsRequestBeforeResponseWithoutReadingTheBody() = runTest {
        val observer = RecordingTrafficObserver()
        val requestHeaders = CompletableDeferred<String>()
        val server = ServerSocket(0).apply { soTimeout = 10_000 }
        val responder = thread(isDaemon = true) {
            runCatching {
                server.accept().use { socket ->
                    socket.soTimeout = 10_000
                    val reader = socket.getInputStream().bufferedReader()
                    val headers = buildString {
                        var line = reader.readLine()
                        while (!line.isNullOrEmpty()) {
                            appendLine(line)
                            line = reader.readLine()
                        }
                    }
                    requestHeaders.complete(headers)
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 413 Payload Too Large\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".encodeToByteArray())
                        flush()
                    }
                }
            }.onFailure { requestHeaders.completeExceptionally(it) }
        }
        val engine = engine(observer)
        val client = HttpClient(engine)
        var reads = 0
        val url = "http://127.0.0.1:${server.localPort}/upload?token=secret"
        try {
            val response = client.post(url) {
                header("Expect", "100-continue")
                setBody(object : OutgoingContent.ReadChannelContent() {
                    override val contentLength: Long = 7
                    override fun readFrom(): ByteReadChannel {
                        reads++
                        return ByteReadChannel("payload")
                    }
                })
            }
            assertTrue(requestHeaders.await().contains("Expect: 100-continue", ignoreCase = true))
            assertEquals(0, reads)
            assertEquals(413, response.status.value)
            assertEquals(listOf("request", "response"), observer.events)
            assertEquals("POST", observer.requestMethod)
            assertEquals(url, observer.requestUrl)
            assertEquals(listOf("100-continue"), observer.requestHeaders["Expect"])
            assertNull(observer.requestBody)
        } finally {
            client.close()
            engine.close()
            engine.coroutineContext.job.join()
            server.close()
            responder.join(1_000)
        }
    }

    @Test
    fun defaultEngineDoesNotInstallTrafficInterceptors() = runTest {
        val engine = engine(null)
        val client = HttpClient(engine)
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                val bytes = "unchanged response".encodeToByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
            start()
        }
        try {
            assertTrue(engine.config.preconfigured!!.networkInterceptors.isEmpty())
            assertEquals("unchanged response", client.get("http://127.0.0.1:${server.address.port}/").bodyAsText())
        } finally {
            client.close()
            engine.close()
            engine.coroutineContext.job.join()
            server.stop(0)
        }
    }

    private fun engine(observer: HttpTrafficObserver?): OkHttpEngine =
        (defaultHttpEngine(null, null, certificatePinning = emptyMap(), httpTrafficObserver = observer) as OkHttpEngine).apply {
            // The production engine is TLS-only. Allow the loopback HTTP fixture in this test.
            config.preconfigured = config.preconfigured!!.newBuilder()
                .connectionSpecs(listOf(ConnectionSpec.CLEARTEXT)).build()
        }
}

internal class RecordingTrafficObserver : HttpTrafficObserver {
    val events = mutableListOf<String>()
    var requestMethod: String? = null
    var requestUrl: String? = null
    var requestHeaders: Map<String, List<String>> = emptyMap()
    var requestBody: ByteArray? = null
    var responseStatus: Int? = null
    var responseHeaders: Map<String, List<String>> = emptyMap()
    var responseBody: ByteArray? = null

    override fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?) {
        events += "request"
        requestMethod = method
        requestUrl = url
        requestHeaders = headers
        requestBody = body
    }

    override fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?) {
        events += "response"
        responseStatus = statusCode
        responseHeaders = headers
        responseBody = body
    }
}
