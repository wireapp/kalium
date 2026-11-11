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

import com.wire.kalium.linkpreview.fetch.FetchedPreviewImage
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.asset.FakeKaliumFileSystem
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun givenLargeImage_whenFetchingImage_thenStoresOnlyBoundedThumbnail() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        val repository = newRepository(
            fileSystem = fileSystem,
            image = { FetchedPreviewImage(PreviewImageTestData.PNG_1600_BY_800, "image/png") }
        )

        val result = assertNotNull(repository.fetchImage("https://example.com/image.png").getOrNull())

        val storedBytes = fileSystem.readByteArray(assertNotNull(result.assetDataPath))
        val storedMetadata = assertNotNull(readImageMetadata(storedBytes, result.mimeType))
        assertEquals("image/png", result.mimeType)
        assertEquals(storedMetadata.width, result.assetWidth)
        assertEquals(storedMetadata.height, result.assetHeight)
        assertEquals(storedBytes.size.toLong(), result.assetDataSize)
        assertTrue(result.assetWidth in 1..MAX_PREVIEW_THUMBNAIL_DIMENSION)
        assertTrue(result.assetHeight in 1..MAX_PREVIEW_THUMBNAIL_DIMENSION)
        assertEquals(result.assetWidth, result.assetHeight * 2)
    }

    @Test
    fun givenExcessiveDimensions_whenFetchingImage_thenReturnsNoAssetAndWritesNoFile() = runTest {
        val fileSystem = FakeKaliumFileSystem()
        val repository = newRepository(
            fileSystem = fileSystem,
            image = { FetchedPreviewImage(PreviewImageTestData.PNG_6000_BY_6000, "image/png") }
        )

        assertNull(repository.fetchImage("https://example.com/image.png").getOrNull())
        assertEquals(emptyList(), fileSystem.listDirectories(fileSystem.rootCachePath))
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
        fetchHtmlHead = htmlHead,
        fetchImageData = image,
        kaliumFileSystem = fileSystem
    )
}
