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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import okio.Buffer
import kotlin.uuid.Uuid

/**
 * Repository for link preview data fetching and parsing.
 */
internal interface LinkPreviewRepository {
    /**
     * Fetches and parses Open Graph metadata from a URL.
     *
     * @param url The URL to fetch metadata from.
     * @param originalUrl The original URL (used as fallback for og:url).
     * @return Either a CoreFailure or the parsed OpenGraphData, or null if parsing fails.
     */
    suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?>

    /**
     * Fetches image bytes from a URL (stub for MVP — returns null).
     *
     * @param imageUrl The image URL to fetch.
     * @return Either a CoreFailure or the image ByteArray.
     */
    suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?>
}

/**
 * Default implementation of LinkPreviewRepository.
 */
@Suppress("TooGenericExceptionCaught", "MagicNumber")
internal class LinkPreviewRepositoryImpl(
    private val httpClient: HttpClient,
    private val kaliumFileSystem: KaliumFileSystem
) : LinkPreviewRepository {
    override suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?> {
        return try {
            val htmlHead = fetchHead(url) ?: return Either.Right(null)
            val ogData = OpenGraphScanner.scan(htmlHead, originalUrl)
            Either.Right(ogData)
        } catch (e: Exception) {
            Either.Right(null) // Graceful degradation
        }
    }

    override suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?> {
        return try {
            val downloadedImage = fetchImageData(imageUrl) ?: return Either.Right(null)
            Either.Right(downloadedImage.toLinkPreviewAsset(imageUrl))
        } catch (_: Exception) {
            Either.Right(null)
        }
    }

    private suspend fun fetchHead(url: String): String? {
        return try {
            httpClient.prepareGet(url).execute { resp ->
                // Only process successful responses
                if (resp.status.value >= 400) return@execute null

                // Only process HTML content
                val contentType = resp.headers[HttpHeaders.ContentType].orEmpty()
                if (!contentType.contains("text/html", ignoreCase = true)) return@execute null

                // Read response body
                val body = resp.bodyAsText()
                sliceHead(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun sliceHead(html: String): String? {
        if (html.isEmpty()) return null

        val lowerHtml = html.lowercase()
        val headStart = lowerHtml.indexOf("<head")
        val headEnd = lowerHtml.indexOf("</head>")

        return if (headStart in 0..<headEnd) {
            // Include the full <head> opening and </head> closing tags
            val adjustedStart = html.lastIndexOf('<', headStart)
            val adjustedEnd = headEnd + 7 // Include "</head>"
            html.substring(adjustedStart, adjustedEnd)
        } else if (headStart >= 0) {
            // Include opening tag to end
            html.substring(html.lastIndexOf('<', headStart))
        } else {
            html
        }
    }

    private suspend fun fetchImageData(imageUrl: String): DownloadedImage? {
        return try {
            httpClient.prepareGet(imageUrl).execute { resp ->
                if (resp.status.value >= 400) return@execute null

                val contentType = resp.headers[HttpHeaders.ContentType].orEmpty()
                if (!contentType.contains("image", ignoreCase = true)
                    || contentType.contains("svg", ignoreCase = true)
                ) {
                    return@execute null
                }

                val bytes = resp.bodyAsBytes()
                if (bytes.isEmpty()) return@execute null

                DownloadedImage(
                    bytes = bytes,
                    mimeType = contentType.substringBefore(';').trim()
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun DownloadedImage.toLinkPreviewAsset(imageUrl: String): LinkPreviewAsset? {
        val metadata = readImageMetadata(bytes, mimeType)
        if (metadata == null) {
            return null
        }
        val tempPath = kaliumFileSystem.tempFilePath("link-preview-${Uuid.random()}")
        val source = Buffer().write(bytes)
        val sink = kaliumFileSystem.sink(tempPath, mustCreate = true)
        kaliumFileSystem.writeData(sink, source)
        val assetName = imageUrl.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() }

        return LinkPreviewAsset(
            mimeType = metadata.mimeType,
            assetDataPath = tempPath,
            assetDataSize = bytes.size.toLong(),
            assetHeight = metadata.height,
            assetWidth = metadata.width,
            assetName = assetName
        )
    }
}

internal data class DownloadedImage(
    val bytes: ByteArray,
    val mimeType: String
)
