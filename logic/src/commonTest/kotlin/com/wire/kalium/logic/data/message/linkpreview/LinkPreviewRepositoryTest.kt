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
package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.ByteReadChannel
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalEncodingApi::class, ExperimentalCoroutinesApi::class)
class LinkPreviewRepositoryTest {

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
        val repository = newRepository(
            onRequest = requests::add
        )

        urls.forEach { url -> assertNull(repository.fetchOpenGraph(url, url).getOrNull(), url) }
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenRedirectToPrivateLiteral_whenFetching_thenNeverRequestsRedirectTarget() = runTest {
        val requests = mutableListOf<String>()
        val repository = newRepository(
            responses = mapOf("https://example.com/start" to redirectResponse("https://10.0.0.1/secret")),
            onRequest = requests::add
        )

        assertNull(repository.fetchOpenGraph("https://example.com/start", "https://example.com/start").getOrNull())
        assertEquals(listOf("https://example.com/start"), requests)
    }

    @Test
    fun givenPrivateImageDestination_whenFetchingImage_thenDoesNotMakeRequest() = runTest {
        val requests = mutableListOf<String>()
        val repository = newRepository(onRequest = requests::add)

        assertNull(repository.fetchImage("https://192.168.1.1/image.png").getOrNull())
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenImageRedirectToPrivateDestination_whenFetchingImage_thenStopsBeforePrivateRequest() = runTest {
        val requests = mutableListOf<String>()
        val repository = newRepository(
            responses = mapOf("https://example.com/image.png" to redirectResponse("https://[::1]/image.png")),
            onRequest = requests::add
        )

        assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
        assertEquals(listOf("https://example.com/image.png"), requests)
    }

    @Test
    fun givenFiveRedirects_whenFetching_thenReturnsPreviewWithinHopLimit() = runTest {
        val responses = (0 until 5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        } + ("https://example.com/5" to htmlResponse("<head><title>Final page</title></head>"))
        val requests = mutableListOf<String>()
        val repository = newRepository(responses = responses, onRequest = requests::add)

        assertNotNull(repository.fetchOpenGraph("https://example.com/0", "https://example.com/0").getOrNull())
        assertEquals(6, requests.size)
    }

    @Test
    fun givenMoreThanFiveDistinctRedirects_whenFetching_thenStopsAtHopLimit() = runTest {
        val responses = (0..5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        }
        val requests = mutableListOf<String>()
        val repository = newRepository(responses = responses, onRequest = requests::add)

        assertNull(repository.fetchOpenGraph("https://example.com/0", "https://example.com/0").getOrNull())
        assertEquals((0..5).map { "https://example.com/$it" }, requests)
    }

    @Test
    fun givenRequestExceedsOverallBudget_whenFetching_thenReturnsNull() = runTest {
        val requests = mutableListOf<String>()
        val repository = newRepository(
            responseDelayMillis = 10_001,
            dispatcher = StandardTestDispatcher(testScheduler),
            onRequest = requests::add
        )

        assertNull(repository.fetchOpenGraph("https://example.com", "https://example.com").getOrNull())
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals(listOf("https://example.com"), requests)
    }

    @Test
    fun givenRedirectsExceedCombinedBudget_whenFetching_thenDoesNotResetDeadlinePerHop() = runTest {
        val responses = (0..5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        }
        val requests = mutableListOf<String>()
        val repository = newRepository(
            responses = responses,
            responseDelayMillis = 4_000,
            dispatcher = StandardTestDispatcher(testScheduler),
            onRequest = requests::add
        )

        assertNull(repository.fetchOpenGraph("https://example.com/0", "https://example.com/0").getOrNull())
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals((0..2).map { "https://example.com/$it" }, requests)
    }

    @Test
    fun givenImageRequestExceedsOverallBudget_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(responseDelayMillis = 10_001, dispatcher = StandardTestDispatcher(testScheduler))

        assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
        assertEquals(10_000L, testScheduler.currentTime)
    }

    @Test
    fun givenCallerCancellation_whenFetching_thenPropagatesCancellation() = runTest {
        val repository = newRepository(responseDelayMillis = 5_000, dispatcher = StandardTestDispatcher(testScheduler))
        val request = async { repository.fetchOpenGraph("https://example.com", "https://example.com") }
        runCurrent()

        request.cancel()

        assertFailsWith<CancellationException> { request.await() }
    }

    @Test
    fun givenValidOgPage_whenFetchingOpenGraph_thenReturnsPreviewData() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/page" to htmlResponse(
                    """
                    <html>
                        <head>
                            <meta property="og:title" content="Example title" />
                            <meta property="og:url" content="https://example.com/og" />
                        </head>
                    </html>
                    """.trimIndent()
                )
            )
        )

        val result = repository.fetchOpenGraph(
            url = "https://example.com/page",
            originalUrl = "https://example.com/page"
        ).getOrNull()

        assertNotNull(result)
        assertEquals("Example title", result.title)
        assertEquals("https://example.com/og", result.url)
    }

    @Test
    fun givenNonHttpsUrl_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository()

        val result = repository.fetchOpenGraph(
            url = "http://example.com/page",
            originalUrl = "http://example.com/page"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenLocalHostTarget_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository()

        val result = repository.fetchOpenGraph(
            url = "https://localhost/page",
            originalUrl = "https://localhost/page"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenThreeRedirects_whenFetchingOpenGraph_thenReturnsPreviewData() = runTest {
        val repository = newRepository(
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

        val result = repository.fetchOpenGraph(
            url = "https://example.com/start",
            originalUrl = "https://example.com/start"
        ).getOrNull()

        assertNotNull(result)
        assertEquals("Redirected title", result.title)
        assertEquals("https://example.com/start", result.url)
    }

    @Test
    fun givenRedirectLoop_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/start" to redirectResponse("https://example.com/next"),
                "https://example.com/next" to redirectResponse("https://example.com/start")
            )
        )

        val result = repository.fetchOpenGraph(
            url = "https://example.com/start",
            originalUrl = "https://example.com/start"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenMissingRedirectLocation_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/start" to MockResponse(
                    status = HttpStatusCode.Found,
                    body = ByteArray(0)
                )
            )
        )

        val result = repository.fetchOpenGraph(
            url = "https://example.com/start",
            originalUrl = "https://example.com/start"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenResponseExceedsMetadataCapBeforeHead_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/page" to htmlResponse("x".repeat(METADATA_CAP_BYTES + 32))
            )
        )

        val result = repository.fetchOpenGraph(
            url = "https://example.com/page",
            originalUrl = "https://example.com/page"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenHeadEndsBeforeLargeBodyTail_whenFetchingOpenGraph_thenReturnsPreviewData() = runTest {
        val repository = newRepository(
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

        val result = repository.fetchOpenGraph(
            url = "https://example.com/page",
            originalUrl = "https://example.com/page"
        ).getOrNull()

        assertNotNull(result)
        assertEquals("Large tail title", result.title)
    }

    @Test
    fun givenRuntimeExceptionDuringOpenGraphFetch_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository(
            thrownException = RuntimeException("timeout")
        )

        val result = repository.fetchOpenGraph(
            url = "https://example.com/page",
            originalUrl = "https://example.com/page"
        ).getOrNull()

        assertNull(result)
    }

    @Test
    fun givenRasterImageResponse_whenFetchingImage_thenReturnsPreviewAsset() = runTest {
        val imageBytes = Base64.decode(ONE_BY_ONE_PNG_BASE64)
        val fileSystem = FakeKaliumFileSystem()
        val repository = newRepository(
            fileSystem = fileSystem,
            responses = mapOf(
                "https://example.com/image.png" to imageResponse(
                    contentType = "image/png",
                    body = imageBytes
                )
            )
        )

        val result = repository.fetchImage("https://example.com/image.png").getOrNull()

        assertNotNull(result)
        assertEquals("image/png", result.mimeType)
        assertEquals(1, result.assetWidth)
        assertEquals(1, result.assetHeight)
        assertEquals(imageBytes.size.toLong(), result.assetDataSize)
        val assetDataPath = result.assetDataPath
        assertNotNull(assetDataPath)
        assertContentEquals(imageBytes, fileSystem.readByteArray(assetDataPath))
    }

    @Test
    fun givenSvgImageResponse_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/image.svg" to imageResponse(
                    contentType = "image/svg+xml",
                    body = "<svg></svg>".encodeToByteArray()
                )
            )
        )

        val result = repository.fetchImage("https://example.com/image.svg").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenNonImageResponse_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/file.txt" to imageResponse(
                    contentType = "text/plain",
                    body = "not-an-image".encodeToByteArray()
                )
            )
        )

        val result = repository.fetchImage("https://example.com/file.txt").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenUnreadableImageBytes_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/broken.png" to imageResponse(
                    contentType = "image/png",
                    body = "not-a-real-png".encodeToByteArray()
                )
            )
        )

        val result = repository.fetchImage("https://example.com/broken.png").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenOversizedImageByContentLength_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/image.png" to imageResponse(
                    contentType = "image/png",
                    body = byteArrayOf(1, 2, 3),
                    contentLength = IMAGE_CAP_BYTES.toLong() + 1
                )
            )
        )

        val result = repository.fetchImage("https://example.com/image.png").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenOversizedImageStreamWithoutContentLength_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/image.png" to imageResponse(
                    contentType = "image/png",
                    body = ByteArray(IMAGE_CAP_BYTES + 1) { 1 }
                )
            )
        )

        val result = repository.fetchImage("https://example.com/image.png").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenRuntimeExceptionDuringImageFetch_whenFetchingImage_thenReturnsNull() = runTest {
        val repository = newRepository(
            thrownException = RuntimeException("timeout")
        )

        val result = repository.fetchImage("https://example.com/image.png").getOrNull()

        assertNull(result)
    }

    private fun TestScope.newRepository(
        fileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem(),
        responses: Map<String, MockResponse> = emptyMap(),
        thrownException: Throwable? = null,
        responseDelayMillis: Long = 0,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        onRequest: (String) -> Unit = {}
    ): LinkPreviewRepository {
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

        return LinkPreviewRepositoryImpl(
            httpClient = HttpClient(mockEngine) {
                followRedirects = false
                expectSuccess = false
            },
            kaliumFileSystem = fileSystem
        )
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
        const val ONE_BY_ONE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW4QAAAAASUVORK5CYII="
    }
}
