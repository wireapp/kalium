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
@file:OptIn(InternalKaliumApi::class)

package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.linkpreview.fetch.FetchedPreviewImage
import com.wire.kalium.linkpreview.fetch.LinkPreviewFetcher
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalEncodingApi::class, ExperimentalCoroutinesApi::class)
class LinkPreviewRepositoryTest {

    @Test
    fun givenValidHtmlHead_whenFetchingOpenGraph_thenParsesMetadata() = runTest {
        val fetchedUrls = mutableListOf<String>()
        val repository = newRepository(
            htmlHead = {
                fetchedUrls.add(it)
                """
                <head>
                    <meta property="og:title" content="Example title" />
                    <meta property="og:url" content="https://example.com/og" />
                </head>
                """.trimIndent()
            }
        )

        val result = repository.fetchOpenGraph("https://example.com/page", "https://example.com/original").getOrNull()

        assertNotNull(result)
        assertEquals("Example title", result.title)
        assertEquals("https://example.com/og", result.url)
        assertEquals(listOf("https://example.com/page"), fetchedUrls)
    }

    @Test
    fun givenHtmlWithoutOgUrl_whenFetchingOpenGraph_thenPreservesOriginalUrl() = runTest {
        val repository = newRepository(htmlHead = { "<head><title>Example title</title></head>" })

        val result = repository.fetchOpenGraph("https://example.com/redirected", "https://example.com/original").getOrNull()

        assertNotNull(result)
        assertEquals("https://example.com/original", result.url)
    }

    @Test
    fun givenUnavailableHtml_whenFetchingOpenGraph_thenReturnsNoPreview() = runTest {
        val repository = newRepository()

        assertNull(repository.fetchOpenGraph("https://example.com/page", "https://example.com/page").getOrNull())
    }

    @Test
    fun givenFetcherFailure_whenFetchingOpenGraph_thenDegradesGracefully() = runTest {
        val repository = newRepository(htmlHead = { error("Fetch failed") })

        assertNull(repository.fetchOpenGraph("https://example.com/page", "https://example.com/page").getOrNull())
    }

    @Test
    fun givenRasterImage_whenFetchingImage_thenWritesAssetAndReadsMetadata() = runTest {
        val imageBytes = Base64.decode(ONE_BY_ONE_PNG_BASE64)
        val fileSystem = FakeKaliumFileSystem()
        val fetchedUrls = mutableListOf<String>()
        val repository = newRepository(
            fileSystem = fileSystem,
            image = {
                fetchedUrls.add(it)
                FetchedPreviewImage(imageBytes, "image/png")
            }
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
        assertEquals(listOf("https://example.com/image.png"), fetchedUrls)
    }

    @Test
    fun givenUnreadableImageBytes_whenFetchingImage_thenReturnsNoAsset() = runTest {
        val repository = newRepository(image = { FetchedPreviewImage("not-a-real-png".encodeToByteArray(), "image/png") })

        assertNull(repository.fetchImage("https://example.com/broken.png").getOrNull())
    }

    @Test
    fun givenUnavailableImage_whenFetchingImage_thenReturnsNoAsset() = runTest {
        val repository = newRepository()

        assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
    }

    @Test
    fun givenFetcherFailure_whenFetchingImage_thenDegradesGracefully() = runTest {
        val repository = newRepository(image = { error("Fetch failed") })

        assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
    }

    @Test
    fun givenCallerCancellation_whenFetchingOpenGraph_thenPropagatesCancellation() = runTest {
        val repository = newRepository(htmlHead = { awaitCancellation() })
        val request = async { repository.fetchOpenGraph("https://example.com/page", "https://example.com/page") }
        runCurrent()

        request.cancel()

        assertFailsWith<CancellationException> { request.await() }
    }

    @Test
    fun givenCallerCancellation_whenFetchingImage_thenPropagatesCancellation() = runTest {
        val repository = newRepository(image = { awaitCancellation() })
        val request = async { repository.fetchImage("https://example.com/image.png") }
        runCurrent()

        request.cancel()

        assertFailsWith<CancellationException> { request.await() }
    }

    private fun newRepository(
        fileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem(),
        htmlHead: suspend (String) -> String? = { null },
        image: suspend (String) -> FetchedPreviewImage? = { null }
    ): LinkPreviewRepository = LinkPreviewRepositoryImpl(
        fetcher = object : LinkPreviewFetcher {
            override suspend fun fetchHtmlHead(url: String): String? = htmlHead(url)
            override suspend fun fetchImage(url: String): FetchedPreviewImage? = image(url)
            override suspend fun close() = Unit
        },
        kaliumFileSystem = fileSystem
    )

    private companion object {
        const val ONE_BY_ONE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW4QAAAAASUVORK5CYII="
    }
}
