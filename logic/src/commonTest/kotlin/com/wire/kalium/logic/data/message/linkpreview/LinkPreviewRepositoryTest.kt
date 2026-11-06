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
        val repository = newRepository(
            onRequest = requests::add
        )

        urls.forEach { url ->
            assertNull(repository.fetchHtmlHead(url), url)
            assertNull(repository.fetchImage(url).getOrNull(), url)
        }
        assertEquals(emptyList(), requests)
    }

    @Test
    fun givenPrivateRedirect_whenFetchingHtmlOrImage_thenNeverRequestsPrivateTarget() = runTest {
        val requests = mutableListOf<String>()
        val repository = newRepository(
            responses = mapOf("https://example.com/start" to redirectResponse("https://[::1]/secret")),
            onRequest = requests::add
        )

        assertNull(repository.fetchHtmlHead("https://example.com/start"))
        assertNull(repository.fetchImage("https://example.com/start").getOrNull())
        assertEquals(listOf("https://example.com/start", "https://example.com/start"), requests)
    }

    @Test
    fun givenMoreThanFiveDistinctRedirects_whenFetching_thenStopsAtHopLimit() = runTest {
        val responses = (0..5).associate { step ->
            "https://example.com/$step" to redirectResponse("https://example.com/${step + 1}")
        }
        val requests = mutableListOf<String>()
        val repository = newRepository(responses = responses, onRequest = requests::add)

        assertNull(repository.fetchHtmlHead("https://example.com/0"))
        assertEquals((0..5).map { "https://example.com/$it" }, requests)
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

        assertNull(repository.fetchHtmlHead("https://example.com/0"))
        assertEquals(10_000L, testScheduler.currentTime)
        assertEquals((0..2).map { "https://example.com/$it" }, requests)
    }

    @Test
    fun givenCallerCancellation_whenFetching_thenPropagatesCancellation() = runTest {
        val repository = newRepository(responseDelayMillis = 5_000, dispatcher = StandardTestDispatcher(testScheduler))
        val request = async { repository.fetchHtmlHead("https://example.com") }
        runCurrent()

        request.cancel()

        assertFailsWith<CancellationException> { request.await() }
    }

    @Test
    fun givenResponseExceedsMetadataCapBeforeHead_whenFetchingOpenGraph_thenReturnsNull() = runTest {
        val repository = newRepository(
            responses = mapOf(
                "https://example.com/page" to htmlResponse("x".repeat(METADATA_CAP_BYTES + 32))
            )
        )

        val result = repository.fetchHtmlHead(
            url = "https://example.com/page"
        )

        assertNull(result)
    }

    @Test
    fun givenSvgOrNonImageContent_whenFetchingImage_thenRejectsContent() = runTest {
        listOf("image/svg+xml", "text/plain").forEach { mimeType ->
            val repository = newRepository(
                responses = mapOf("https://example.com/image" to imageResponse(mimeType, "<svg/>".encodeToByteArray()))
            )

            assertNull(repository.fetchImage("https://example.com/image").getOrNull(), mimeType)
        }
    }

    @Test
    fun givenOversizedImageWithOrWithoutContentLength_whenFetching_thenRejectsContent() = runTest {
        val responses = listOf(
            imageResponse("image/png", byteArrayOf(1), contentLength = IMAGE_CAP_BYTES.toLong() + 1),
            imageResponse("image/png", ByteArray(IMAGE_CAP_BYTES + 1), contentLength = null)
        )
        responses.forEach { response ->
            val repository = newRepository(responses = mapOf("https://example.com/image.png" to response))

            assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
        }
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

    private suspend fun LinkPreviewRepository.fetchHtmlHead(url: String): OpenGraphData? =
        fetchOpenGraph(url, url).getOrNull()

    private fun TestScope.newRepository(
        fileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem(),
        responses: Map<String, MockResponse> = emptyMap(),
        responseDelayMillis: Long = 0,
        dispatcher: CoroutineDispatcher = StandardTestDispatcher(testScheduler),
        onRequest: (String) -> Unit = {}
    ): LinkPreviewRepository {
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
