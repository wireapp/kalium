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

import okhttp3.Call
import okhttp3.Connection
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HttpTrafficObserverInterceptorTest {

    @Test
    fun givenARequestWithABody_whenIntercepting_thenTheObserverSeesTheRequestBody() {
        // given
        val observedRequests = mutableListOf<Triple<String, String, ByteArray?>>()
        val observer = object : HttpTrafficObserver {
            override fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?) {
                observedRequests.add(Triple(method, url, body))
            }
            override fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?) {
                // not under test here
            }
        }
        val request = Request.Builder()
            .url("https://example.com/path")
            .post("hello world".toRequestBody("text/plain".toMediaType()))
            .build()
        val chain = fakeChain(request) { req -> okResponseFor(req, "response body") }

        // when
        httpTrafficObserverInterceptor(observer).intercept(chain)

        // then
        assertEquals(1, observedRequests.size)
        val (method, url, body) = observedRequests.first()
        assertEquals("POST", method)
        assertEquals("https://example.com/path", url)
        assertEquals("hello world", body?.decodeToString())
    }

    @Test
    fun givenAGzipEncodedResponse_whenIntercepting_thenTheObserverSeesTheDecodedResponseBodyAndTheResponseStillWorks() {
        // given
        var observedBody: ByteArray? = null
        var observedStatus: Int? = null
        val observer = object : HttpTrafficObserver {
            override fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?) = Unit
            override fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?) {
                observedBody = body
                observedStatus = statusCode
            }
        }
        val request = Request.Builder().url("https://example.com/gzip").build()
        val gzippedContent = gzip("gzipped payload")
        val chain = fakeChain(request) { req ->
            okResponseBuilder(req)
                .header("content-encoding", "gzip")
                .body(gzippedContent.toResponseBody(null))
                .build()
        }

        // when
        val result = httpTrafficObserverInterceptor(observer).intercept(chain)

        // then
        assertEquals(200, observedStatus)
        assertEquals("gzipped payload", observedBody?.decodeToString())
        // the interceptor must hand back a response whose body is still readable by callers downstream
        assertEquals("gzipped payload", result.body.string())
        // the body handed back is already decoded: a stale "content-encoding: gzip" header would make
        // callers further up the chain (e.g. Ktor's ContentEncoding plugin) try to gzip-decode it again
        assertNull(result.header("content-encoding"))
    }

    @Test
    fun givenARequestWithNoBody_whenIntercepting_thenTheObserverSeesANullBody() {
        // given
        var observedBody: ByteArray? = "sentinel".encodeToByteArray()
        val observer = object : HttpTrafficObserver {
            override fun onRequest(method: String, url: String, headers: Map<String, List<String>>, body: ByteArray?) {
                observedBody = body
            }
            override fun onResponse(method: String, url: String, statusCode: Int, headers: Map<String, List<String>>, body: ByteArray?) = Unit
        }
        val request = Request.Builder().url("https://example.com/no-body").build()
        val chain = fakeChain(request) { req -> okResponseFor(req, "") }

        // when
        httpTrafficObserverInterceptor(observer).intercept(chain)

        // then
        assertNull(observedBody)
    }

    private fun gzip(content: String): ByteArray {
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.writeUtf8(content) }
        return buffer.readByteArray()
    }

    private fun okResponseBuilder(request: Request): Response.Builder = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")

    private fun okResponseFor(request: Request, body: String): Response = okResponseBuilder(request)
        .body(body.toResponseBody("text/plain".toMediaType()))
        .build()

    private fun fakeChain(request: Request, respond: (Request) -> Response): Interceptor.Chain =
        object : Interceptor.Chain {
            override fun call(): Call = throw NotImplementedError()
            override fun connectTimeoutMillis(): Int = 0
            override fun connection(): Connection? = null
            override fun proceed(request: Request): Response = respond(request)
            override fun readTimeoutMillis(): Int = 0
            override fun request(): Request = request
            override fun withConnectTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun withReadTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun withWriteTimeout(timeout: Int, unit: TimeUnit): Interceptor.Chain = this
            override fun writeTimeoutMillis(): Int = 0
        }
}
