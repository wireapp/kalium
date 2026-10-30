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
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class LinkPreviewFetcherTest {

    @Test
    fun givenUnsafeUrlForm_whenFetching_thenRejectsBeforeHttp() = runTest {
        val urls = listOf(
            "http://example.com/page",
            "https://localhost/page",
            "https://user:password@example.com/page",
            "https://127.0.0.1/page",
            "https://[::1]/page",
            "https://[0:0:0:0:0:0:0:1]/page",
            "https://printer.local./page",
            "https://service.localhost/page"
        )
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            onRequest = requests::add
        )

        urls.forEach { url ->
            assertNull(fetcher.fetchHtmlHead(url), url)
            assertNull(fetcher.fetchImage(url), url)
        }
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenPrivateRedirect_whenFetchingHtmlOrImage_thenNeverRequestsPrivateTarget() = runTest {
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            responses = mapOf("https://example.com/start" to redirectResponse("https://[::1]/secret")),
            onRequest = requests::add
        )

        assertNull(fetcher.fetchHtmlHead("https://example.com/start"))
        assertNull(fetcher.fetchImage("https://example.com/start"))
        assertEquals(listOf("https://example.com/start", "https://example.com/start"), requests)
    }

    @Test
    fun givenMoreThanFiveDistinctRedirects_whenFetching_thenStopsAtHopLimit() = runTest {
        val responses = (0..5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        }
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(responses = responses, onRequest = requests::add)

        assertNull(fetcher.fetchHtmlHead("https://example.com/0"))
        assertEquals((0..5).map { "https://example.com/$it" }, requests)
    }

    @Test
    fun givenRedirectsExceedCombinedBudget_whenFetching_thenDoesNotResetDeadlinePerHop() = runTest {
        val responses = (0..5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        }
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            responses = responses,
            responseDelayMillis = 4_000,
            dispatcher = StandardTestDispatcher(testScheduler),
            onRequest = requests::add
        )

        assertNull(fetcher.fetchHtmlHead("https://example.com/0"))
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals((0..2).map { "https://example.com/$it" }, requests)
    }

    @Test
    fun givenCallerCancellation_whenFetching_thenPropagatesCancellation() = runTest {
        val fetcher = newFetcher(responseDelayMillis = 5_000, dispatcher = StandardTestDispatcher(testScheduler))
        val request = async { fetcher.fetchHtmlHead("https://example.com") }
        runCurrent()

        request.cancel()

        assertFailsWith<CancellationException> { request.await() }
    }

    @Test
    fun givenResponseExceedsMetadataCapBeforeHead_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/page" to htmlResponse("x".repeat(METADATA_CAP_BYTES + 32))
            )
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/page"
        )

        assertNull(result)
    }

    @Test
    fun givenSvgOrNonImageContent_whenFetchingImage_thenRejectsContent() = runTest {
        listOf("image/svg+xml", "text/plain").forEach { mimeType ->
            val fetcher = newFetcher(
                responses = mapOf("https://example.com/image" to imageResponse(mimeType, "<svg/>".encodeToByteArray()))
            )

            assertNull(fetcher.fetchImage("https://example.com/image"), mimeType)
        }
    }

    @Test
    fun givenOversizedImageWithOrWithoutContentLength_whenFetching_thenRejectsContent() = runTest {
        val responses = listOf(
            imageResponse("image/png", byteArrayOf(1), contentLength = IMAGE_CAP_BYTES.toLong() + 1),
            imageResponse("image/png", ByteArray(IMAGE_CAP_BYTES + 1), contentLength = null)
        )
        responses.forEach { response ->
            val fetcher = newFetcher(responses = mapOf("https://example.com/image.png" to response))

            assertNull(fetcher.fetchImage("https://example.com/image.png"))
        }
    }

    @Test
    fun givenClosedFetcher_whenFetching_thenDoesNotCreateOrRebuildClient() = runTest {
        var factoryCalls = 0
        val fetcher = LinkPreviewFetcherImpl {
            factoryCalls++
            error("A closed fetcher must not create a client")
        }
        fetcher.close()
        fetcher.close()

        assertNull(fetcher.fetchHtmlHead("https://example.com/page"))
        assertNull(fetcher.fetchImage("https://example.com/image.png"))
        assertEquals(0, factoryCalls)
    }

    @Test
    fun givenActiveRequest_whenClosing_thenCancelsRequestAndWaitsForCleanup() = runTest {
        val started = CompletableDeferred<Unit>()
        val cleanedUp = CompletableDeferred<Unit>()
        val engine = MockEngine(MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                started.complete(Unit)
                try {
                    delay(Long.MAX_VALUE)
                    error("Request should be cancelled")
                } finally {
                    withContext(NonCancellable) {
                        delay(100)
                        cleanedUp.complete(Unit)
                    }
                }
            }
        })
        val client = HttpClient(engine) {
            followRedirects = false
            expectSuccess = false
        }
        val fetcher = LinkPreviewFetcherImpl { client }
        val request = async { fetcher.fetchHtmlHead("https://example.com/page") }
        started.await()

        val closing = async { fetcher.close() }
        runCurrent()

        assertFalse(closing.isCompleted)
        assertNull(fetcher.fetchHtmlHead("https://example.com/during-shutdown"))
        closing.await()

        assertTrue(cleanedUp.isCompleted)
        assertFailsWith<CancellationException> { request.await() }
        assertNull(fetcher.fetchHtmlHead("https://example.com/other"))
        fetcher.close()
    }

    private fun TestScope.newFetcher(
        responses: Map<String, MockResponse> = emptyMap(),
        responseDelayMillis: Long = 0,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        onRequest: (String) -> Unit = {}
    ): LinkPreviewFetcher {
        val mockEngine = MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                onRequest(request.url.toString())
                delay(responseDelayMillis)
                val response = responses[request.url.toString()]
                    ?: error("No mocked response for ${request.url}")

                respond(
                    content = ByteReadChannel(response.body),
                    status = response.status,
                    headers = response.headers
                )
            }
        })

        return LinkPreviewFetcherImpl {
            HttpClient(mockEngine) {
                followRedirects = false
                expectSuccess = false
            }
        }
    }

    private fun htmlResponse(body: String): MockResponse = MockResponse(
        status = HttpStatusCode.OK,
        body = body.encodeToByteArray(),
        headers = Headers.build {
            append(HttpHeaders.ContentType, "text/html; charset=utf-8")
        }
    )

    private fun imageResponse(
        contentType: String,
        body: ByteArray,
        contentLength: Long? = body.size.toLong()
    ): MockResponse = MockResponse(
        status = HttpStatusCode.OK,
        body = body,
        headers = Headers.build {
            append(HttpHeaders.ContentType, contentType)
            contentLength?.let { append(HttpHeaders.ContentLength, it.toString()) }
        }
    )

    private fun redirectResponse(location: String): MockResponse = MockResponse(
        status = HttpStatusCode.Found,
        body = ByteArray(0),
        headers = Headers.build {
            append(HttpHeaders.Location, location)
        }
    )

    private data class MockResponse(
        val status: HttpStatusCode,
        val body: ByteArray,
        val headers: Headers = Headers.Empty
    )

    private companion object {
        const val METADATA_CAP_BYTES = 64 * 1024
        const val IMAGE_CAP_BYTES = 5 * 1024 * 1024
    }
}
