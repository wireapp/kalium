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

@OptIn(ExperimentalEncodingApi::class)
class LinkPreviewRepositoryTest {

    @Test
    fun givenRasterImageResponse_whenFetchingImage_thenReturnsPreviewAsset() = kotlinx.coroutines.test.runTest {
        val imageBytes = Base64.decode(ONE_BY_ONE_PNG_BASE64)
        val fileSystem = FakeKaliumFileSystem()
        val repository = newRepository(
            fileSystem = fileSystem,
            contentType = "image/png",
            body = imageBytes
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
    fun givenSvgImageResponse_whenFetchingImage_thenReturnsNull() = kotlinx.coroutines.test.runTest {
        val repository = newRepository(
            contentType = "image/svg+xml",
            body = "<svg></svg>".encodeToByteArray()
        )

        val result = repository.fetchImage("https://example.com/image.svg").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenNonImageResponse_whenFetchingImage_thenReturnsNull() = kotlinx.coroutines.test.runTest {
        val repository = newRepository(
            contentType = "text/plain",
            body = "not-an-image".encodeToByteArray()
        )

        val result = repository.fetchImage("https://example.com/file.txt").getOrNull()

        assertNull(result)
    }

    @Test
    fun givenUnreadableImageBytes_whenFetchingImage_thenReturnsNull() = kotlinx.coroutines.test.runTest {
        val repository = newRepository(
            contentType = "image/png",
            body = "not-a-real-png".encodeToByteArray()
        )

        val result = repository.fetchImage("https://example.com/broken.png").getOrNull()

        assertNull(result)
    }

    private fun newRepository(
        fileSystem: FakeKaliumFileSystem = FakeKaliumFileSystem(),
        contentType: String,
        body: ByteArray
    ): LinkPreviewRepository {
        val mockEngine = MockEngine {
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = Headers.build {
                    append(HttpHeaders.ContentType, contentType)
                }
            )
        }

        return LinkPreviewRepositoryImpl(
            httpClient = HttpClient(mockEngine),
            kaliumFileSystem = fileSystem
        )
    }

    private companion object {
        const val ONE_BY_ONE_PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aW4QAAAAASUVORK5CYII="
    }
}
