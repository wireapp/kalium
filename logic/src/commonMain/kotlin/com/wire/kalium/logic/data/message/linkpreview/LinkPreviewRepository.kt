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
@file:Suppress("TooManyFunctions", "ReturnCount")

package com.wire.kalium.logic.data.message.linkpreview

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.asset.KaliumFileSystem
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import kotlin.coroutines.cancellation.CancellationException
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
@Suppress("TooGenericExceptionCaught", "MagicNumber", "CyclomaticComplexMethod", "ComplexCondition")
internal class LinkPreviewRepositoryImpl(
    private val httpClient: HttpClient,
    private val kaliumFileSystem: KaliumFileSystem
) : LinkPreviewRepository {
    override suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?> {
        return try {
            val ogData = withTimeoutOrNull(MAX_PREVIEW_FETCH_MILLIS) {
                val htmlHead = fetchHead(url) ?: return@withTimeoutOrNull null
                OpenGraphScanner.scan(htmlHead, originalUrl)
            }
            Either.Right(ogData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Right(null) // Graceful degradation
        }
    }

    override suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?> {
        return try {
            val image = withTimeoutOrNull(MAX_PREVIEW_FETCH_MILLIS) {
                val downloadedImage = fetchImageData(imageUrl) ?: return@withTimeoutOrNull null
                downloadedImage.toLinkPreviewAsset(imageUrl)
            }
            Either.Right(image)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Either.Right(null)
        }
    }

    private suspend fun fetchHead(url: String): String? {
        return executeGetWithRedirects(url) { resp ->
            val contentType = resp.headers[HttpHeaders.ContentType].orEmpty()
            if (!contentType.contains("text/html", ignoreCase = true)) return@executeGetWithRedirects null
            readBoundedHead(resp)
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
        return executeGetWithRedirects(imageUrl) { resp ->
            val contentType = resp.headers[HttpHeaders.ContentType].orEmpty()
            if (!contentType.contains("image", ignoreCase = true) ||
                contentType.contains("svg", ignoreCase = true)
            ) {
                return@executeGetWithRedirects null
            }

            val mimeType = contentType.substringBefore(';').trim()
            val bytes = readBoundedBytes(
                response = resp,
                maxBytes = MAX_PREVIEW_IMAGE_BYTES
            ) ?: return@executeGetWithRedirects null

            if (bytes.isEmpty()) return@executeGetWithRedirects null

            DownloadedImage(
                bytes = bytes,
                mimeType = mimeType
            )
        }
    }

    private suspend fun DownloadedImage.toLinkPreviewAsset(imageUrl: String): LinkPreviewAsset? {
        val metadata = readImageMetadata(bytes, mimeType) ?: return null
        val tempPath = kaliumFileSystem.tempFilePath("link-preview-${Uuid.random()}")
        val source = Buffer().write(bytes)
        val sink = kaliumFileSystem.sink(tempPath, mustCreate = true)
        kaliumFileSystem.writeData(sink, source)
        val assetName = sanitizePreviewText(
            input = imageUrl.substringAfterLast('/').substringBefore('?'),
            maxLength = MAX_PREVIEW_ASSET_NAME_LENGTH
        )

        return LinkPreviewAsset(
            mimeType = metadata.mimeType,
            assetDataPath = tempPath,
            assetDataSize = bytes.size.toLong(),
            assetHeight = metadata.height,
            assetWidth = metadata.width,
            assetName = assetName
        )
    }

    private suspend fun <T> executeGetWithRedirects(
        initialUrl: String,
        onSuccess: suspend (HttpResponse) -> T?
    ): T? {
        val validatedInitialUrl = validatePreviewTarget(initialUrl) ?: return null
        return executeGetRequest(
            currentUrl = validatedInitialUrl,
            visitedUrls = mutableSetOf(validatedInitialUrl.toString()),
            redirectsRemaining = MAX_PREVIEW_REDIRECTS,
            onSuccess = onSuccess
        )
    }

    private suspend fun <T> executeGetRequest(
        currentUrl: Url,
        visitedUrls: MutableSet<String>,
        redirectsRemaining: Int,
        onSuccess: suspend (HttpResponse) -> T?
    ): T? {
        val outcome = try {
            httpClient.prepareGet(currentUrl.toString()).execute { response ->
                when {
                    response.status.value in HTTP_REDIRECT_STATUS_CODES -> {
                        if (redirectsRemaining == 0) return@execute PreviewRequestOutcome.Failure
                        val location = response.headers[HttpHeaders.Location]
                            ?: return@execute PreviewRequestOutcome.Failure
                        val redirectUrl = validateRedirectTarget(currentUrl, location)
                            ?: return@execute PreviewRequestOutcome.Failure
                        PreviewRequestOutcome.Redirect(redirectUrl)
                    }

                    response.status.value >= HTTP_ERROR_STATUS_CODE -> PreviewRequestOutcome.Failure
                    else -> PreviewRequestOutcome.Success(onSuccess(response))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }

        return when (outcome) {
            is PreviewRequestOutcome.Success -> outcome.value
            is PreviewRequestOutcome.Redirect -> {
                val redirectUrl = outcome.url.takeIf { visitedUrls.add(it.toString()) } ?: return null
                executeGetRequest(
                    currentUrl = redirectUrl,
                    visitedUrls = visitedUrls,
                    redirectsRemaining = redirectsRemaining - 1,
                    onSuccess = onSuccess
                )
            }

            PreviewRequestOutcome.Failure -> null
        }
    }

    private fun validateRedirectTarget(currentUrl: Url, location: String): Url? {
        val resolvedUrl = resolveRedirectUrl(currentUrl, location) ?: return null
        return validatePreviewTarget(resolvedUrl.toString())
    }

    private fun validatePreviewTarget(url: String): Url? {
        val parsedUrl = try {
            Url(url)
        } catch (_: Exception) {
            return null
        }

        if (!isAllowedPreviewUrl(parsedUrl)) return null

        // The platform transport validates the exact DNS answers used by its socket. Resolving
        // here would duplicate DNS work without enforcing which address the connection uses.
        return parsedUrl
    }

    private fun resolveRedirectUrl(currentUrl: Url, location: String): Url? {
        val trimmedLocation = location.trim()
        if (trimmedLocation.isEmpty()) return null

        val resolvedUrl = when {
            trimmedLocation.startsWith("//") -> {
                "${currentUrl.protocol.name}:$trimmedLocation"
            }

            trimmedLocation.startsWith("/") -> {
                "${currentUrl.protocol.name}://${currentUrl.host}${currentUrl.portSuffix()}$trimmedLocation"
            }

            "://" in trimmedLocation -> trimmedLocation
            else -> {
                val parentPath = currentUrl.encodedPath.substringBeforeLast("/", "")
                val separator = if (parentPath.endsWith("/")) "" else "/"
                val normalizedPath = if (parentPath.isEmpty()) {
                    "/$trimmedLocation"
                } else {
                    "$parentPath$separator$trimmedLocation"
                }
                "${currentUrl.protocol.name}://${currentUrl.host}${currentUrl.portSuffix()}$normalizedPath"
            }
        }

        return try {
            URLBuilder(resolvedUrl).build()
        } catch (_: Exception) {
            null
        }
    }

    private fun Url.portSuffix(): String =
        if (port == protocol.defaultPort) "" else ":$port"

    private suspend fun readBoundedHead(response: HttpResponse): String? {
        val channel = response.body<ByteReadChannel>()
        val contentBuilder = StringBuilder()
        val buffer = ByteArray(PREVIEW_READ_BUFFER_SIZE)
        var totalBytesRead = 0
        var foundHeadStart = false

        while (!channel.isClosedForRead) {
            val remainingBytes = MAX_PREVIEW_METADATA_BYTES - totalBytesRead
            if (remainingBytes <= 0) {
                return if (foundHeadStart) sliceHead(contentBuilder.toString()) else null
            }

            val readCount = channel.readAvailable(
                buffer,
                0,
                minOf(buffer.size, remainingBytes)
            )
            if (readCount <= 0) break

            totalBytesRead += readCount
            contentBuilder.append(buffer.decodeToString(0, readCount))

            val lowerCaseHtml = contentBuilder.toString().lowercase()
            if (!foundHeadStart && lowerCaseHtml.contains(HTML_HEAD_START_MARKER)) {
                foundHeadStart = true
            }
            if (lowerCaseHtml.contains(HTML_HEAD_END_MARKER)) {
                return sliceHead(contentBuilder.toString())
            }
        }

        return sliceHead(contentBuilder.toString())
    }

    private suspend fun readBoundedBytes(response: HttpResponse, maxBytes: Int): ByteArray? {
        val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        if (contentLength != null && contentLength > maxBytes.toLong()) return null

        val channel = response.body<ByteReadChannel>()
        val buffer = ByteArray(PREVIEW_READ_BUFFER_SIZE)
        val contentBuffer = Buffer()
        var totalBytesRead = 0

        while (!channel.isClosedForRead) {
            val readCount = channel.readAvailable(buffer, 0, buffer.size)
            if (readCount <= 0) break

            totalBytesRead += readCount
            if (totalBytesRead > maxBytes) return null

            contentBuffer.write(buffer, 0, readCount)
        }

        return contentBuffer.readByteArray()
    }

    private sealed interface PreviewRequestOutcome<out T> {
        data class Success<T>(val value: T?) : PreviewRequestOutcome<T>
        data class Redirect(val url: Url) : PreviewRequestOutcome<Nothing>
        data object Failure : PreviewRequestOutcome<Nothing>
    }

    private companion object {
        const val MAX_PREVIEW_FETCH_MILLIS = 10_000L
        const val MAX_PREVIEW_REDIRECTS = 5
        const val MAX_PREVIEW_METADATA_BYTES = 64 * 1024
        const val MAX_PREVIEW_IMAGE_BYTES = 5 * 1024 * 1024
        const val PREVIEW_READ_BUFFER_SIZE = 8 * 1024
        const val HTTP_ERROR_STATUS_CODE = 400
        val HTTP_REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)
        const val HTML_HEAD_START_MARKER = "<head"
        const val HTML_HEAD_END_MARKER = "</head>"
    }
}

internal data class DownloadedImage(
    val bytes: ByteArray,
    val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as DownloadedImage

        if (!bytes.contentEquals(other.bytes)) return false
        if (mimeType != other.mimeType) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + mimeType.hashCode()
        return result
    }
}
