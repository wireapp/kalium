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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalEncodingApi::class)
class LinkPreviewRepositoryTest {

    @Test
    fun givenValidHtmlHead_whenFetchingOpenGraph_thenParsesMetadata() = runTest {
        val repository = newRepository(
            htmlHead = {
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
    }

    @Test
    fun givenRasterImage_whenFetchingImage_thenWritesAssetAndReadsMetadata() = runTest {
        val imageBytes = Base64.decode(ONE_BY_ONE_PNG_BASE64)
        val fileSystem = FakeKaliumFileSystem()
        val repository = newRepository(
            fileSystem = fileSystem,
            image = {
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
    }

    @Test
    fun givenUnreadableImageBytes_whenFetchingImage_thenReturnsNoAsset() = runTest {
        val repository = newRepository(image = { FetchedPreviewImage("not-a-real-png".encodeToByteArray(), "image/png") })

        assertNull(repository.fetchImage("https://example.com/broken.png").getOrNull())
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
