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
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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

        urls.forEach { url -> assertNull(fetcher.fetchHtmlHead(url), url) }
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenRedirectToPrivateLiteral_whenFetching_thenNeverRequestsRedirectTarget() = runTest {
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            responses = mapOf("https://example.com/start" to redirectResponse("https://10.0.0.1/secret")),
            onRequest = requests::add
        )

        assertNull(fetcher.fetchHtmlHead("https://example.com/start"))
        assertEquals(listOf("https://example.com/start"), requests)
    }

    @Test
    fun givenPrivateImageDestination_whenFetchingImage_thenDoesNotMakeRequest() = runTest {
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(onRequest = requests::add)

        assertNull(fetcher.fetchImage("https://192.168.1.1/image.png"))
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenImageRedirectToPrivateDestination_whenFetchingImage_thenStopsBeforePrivateRequest() = runTest {
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            responses = mapOf("https://example.com/image.png" to redirectResponse("https://[::1]/image.png")),
            onRequest = requests::add
        )

        assertNull(fetcher.fetchImage("https://example.com/image.png"))
        assertEquals(listOf("https://example.com/image.png"), requests)
    }

    @Test
    fun givenFiveRedirects_whenFetching_thenReturnsPreviewWithinHopLimit() = runTest {
        val responses = (0 until 5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        } + ("https://example.com/5" to htmlResponse("<head><title>Final page</title></head>"))
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(responses = responses, onRequest = requests::add)

        assertNotNull(fetcher.fetchHtmlHead("https://example.com/0"))
        assertEquals(6, requests.size)
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
    fun givenRequestExceedsOverallBudget_whenFetching_thenReturnsNull() = runTest {
        val requests = mutableListOf<String>()
        val fetcher = newFetcher(
            responseDelayMillis = 10_001,
            dispatcher = StandardTestDispatcher(testScheduler),
            onRequest = requests::add
        )

        assertNull(fetcher.fetchHtmlHead("https://example.com"))
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals(listOf("https://example.com"), requests)
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
    fun givenImageRequestExceedsOverallBudget_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(responseDelayMillis = 10_001, dispatcher = StandardTestDispatcher(testScheduler))

        assertNull(fetcher.fetchImage("https://example.com/image.png"))
        assertEquals(10_000L, testScheduler.currentTime)
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
    fun givenNonHttpsUrl_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher()

        val result = fetcher.fetchHtmlHead(
            url = "http://example.com/page"
        )

        assertNull(result)
    }

    @Test
    fun givenLocalHostTarget_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher()

        val result = fetcher.fetchHtmlHead(
            url = "https://localhost/page"
        )

        assertNull(result)
    }

    @Test
    fun givenThreeRedirects_whenFetchingOpenGraph_thenReturnsPreviewData() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/start" to redirectResponse("https://example.com/step-1"),
                "https://example.com/step-1" to redirectResponse("https://example.com/step-2"),
                "https://example.com/step-2" to redirectResponse("https://example.com/final"),
                "https://example.com/final" to htmlResponse(
                    """
                    <head>
                        <meta property="og:title" content="Redirected title" />
                    </head>
                    """.trimIndent()
                )
            )
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/start"
        )

        assertNotNull(result)
        assertTrue(result.contains("Redirected title"))
    }

    @Test
    fun givenRedirectLoop_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/start" to redirectResponse("https://example.com/next"),
                "https://example.com/next" to redirectResponse("https://example.com/start")
            )
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/start"
        )

        assertNull(result)
    }

    @Test
    fun givenMissingRedirectLocation_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/start" to MockResponse(
                    status = HttpStatusCode.Found,
                    body = ByteArray(0)
                )
            )
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/start"
        )

        assertNull(result)
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
    fun givenHeadEndsBeforeLargeBodyTail_whenFetchingOpenGraph_thenReturnsPreviewData() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/page" to htmlResponse(
                    buildString {
                        append("<html><head>")
                        append("""<meta property="og:title" content="Large tail title" />""")
                        append("</head>")
                        append("x".repeat(METADATA_CAP_BYTES + 128))
                        append("</html>")
                    }
                )
            )
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/page"
        )

        assertNotNull(result)
        assertTrue(result.contains("Large tail title"))
        assertFalse(result.contains("</html>"))
    }

    @Test
    fun givenRuntimeExceptionDuringOpenGraphFetch_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            thrownException = RuntimeException("timeout")
        )

        val result = fetcher.fetchHtmlHead(
            url = "https://example.com/page"
        )

        assertNull(result)
    }

    @Test
    fun givenSvgImageResponse_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/image.svg" to imageResponse(
                    contentType = "image/svg+xml",
                    body = "<svg></svg>".encodeToByteArray()
                )
            )
        )

        val result = fetcher.fetchImage("https://example.com/image.svg")

        assertNull(result)
    }

    @Test
    fun givenNonImageResponse_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/file.txt" to imageResponse(
                    contentType = "text/plain",
                    body = "not-an-image".encodeToByteArray()
                )
            )
        )

        val result = fetcher.fetchImage("https://example.com/file.txt")

        assertNull(result)
    }

    @Test
    fun givenOversizedImageByContentLength_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/image.png" to imageResponse(
                    contentType = "image/png",
                    body = byteArrayOf(1, 2, 3),
                    contentLength = IMAGE_CAP_BYTES.toLong() + 1
                )
            )
        )

        val result = fetcher.fetchImage("https://example.com/image.png")

        assertNull(result)
    }

    @Test
    fun givenOversizedImageStreamWithoutContentLength_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            responses = mapOf(
                "https://example.com/image.png" to imageResponse(
                    contentType = "image/png",
                    body = ByteArray(IMAGE_CAP_BYTES + 1) { 1 },
                    contentLength = null
                )
            )
        )

        val result = fetcher.fetchImage("https://example.com/image.png")

        assertNull(result)
    }

    @Test
    fun givenRuntimeExceptionDuringImageFetch_whenFetchingImage_thenReturnsNull() = runTest {
        val fetcher = newFetcher(
            thrownException = RuntimeException("timeout")
        )

        val result = fetcher.fetchImage("https://example.com/image.png")

        assertNull(result)
    }

    @Test
    fun givenRasterImage_whenFetching_thenReturnsBoundedBytesAndMimeType() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val fetcher = newFetcher(
            responses = mapOf("https://example.com/image.png" to imageResponse("image/png", bytes))
        )

        val image = fetcher.fetchImage("https://example.com/image.png")

        assertNotNull(image)
        assertContentEquals(bytes, image.bytes)
        assertEquals("image/png", image.mimeType)
    }

    @Test
    fun givenUnusedFetcher_whenClosing_thenDoesNotCreateHttpClient() = runTest {
        var factoryCalls = 0
        val fetcher = LinkPreviewFetcherImpl {
            factoryCalls++
            error("An unused fetcher must not create a client")
        }

        fetcher.close()
        fetcher.close()

        assertEquals(0, factoryCalls)
    }

    @Test
    fun givenClosedFetcher_whenFetching_thenDoesNotCreateOrRebuildClient() = runTest {
        var factoryCalls = 0
        val fetcher = LinkPreviewFetcherImpl {
            factoryCalls++
            error("A closed fetcher must not create a client")
        }
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
        thrownException: Throwable? = null,
        responseDelayMillis: Long = 0,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        onRequest: (String) -> Unit = {}
    ): LinkPreviewFetcher {
        val mockEngine = MockEngine(MockEngineConfig().apply {
            this.dispatcher = dispatcher
            addHandler { request ->
                onRequest(request.url.toString())
                delay(responseDelayMillis)
                thrownException?.let { throw it }
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
