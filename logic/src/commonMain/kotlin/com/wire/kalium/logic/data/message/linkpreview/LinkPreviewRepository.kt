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
import io.ktor.client.call.body
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.URLBuilder
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
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
@Suppress("TooGenericExceptionCaught", "MagicNumber")
internal class LinkPreviewRepositoryImpl(
    private val httpClient: HttpClient,
    private val kaliumFileSystem: KaliumFileSystem,
    private val resolveHostAddresses: suspend (String) -> List<String> = ::resolvePreviewHostAddresses
) : LinkPreviewRepository {
    override suspend fun fetchOpenGraph(url: String, originalUrl: String): Either<CoreFailure, OpenGraphData?> {
        return try {
            val htmlHead = fetchHead(url) ?: return Either.Right(null)
            val ogData = OpenGraphScanner.scan(htmlHead, originalUrl)
            Either.Right(ogData)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Either.Right(null) // Graceful degradation
        }
    }

    override suspend fun fetchImage(imageUrl: String): Either<CoreFailure, LinkPreviewAsset?> {
        return try {
            val downloadedImage = fetchImageData(imageUrl) ?: return Either.Right(null)
            Either.Right(downloadedImage.toLinkPreviewAsset(imageUrl))
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
        val metadata = readImageMetadata(bytes, mimeType)
        if (metadata == null) {
            return null
        }
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
        var currentUrl = validatePreviewTarget(initialUrl) ?: return null
        val visitedUrls = mutableSetOf(currentUrl.toString())

        repeat(MAX_PREVIEW_REDIRECT_HOPS + 1) { hop ->
            val outcome = try {
                httpClient.prepareGet(currentUrl.toString()).execute { response ->
                    when {
                        response.status.value in HTTP_REDIRECT_STATUS_RANGE -> {
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

            when (outcome) {
                is PreviewRequestOutcome.Success -> return outcome.value
                is PreviewRequestOutcome.Redirect -> {
                    if (hop == MAX_PREVIEW_REDIRECT_HOPS) return null
                    if (!visitedUrls.add(outcome.url.toString())) return null
                    currentUrl = outcome.url
                }

                PreviewRequestOutcome.Failure -> return null
            }
        }

        return null
    }

    private suspend fun validateRedirectTarget(currentUrl: Url, location: String): Url? {
        val resolvedUrl = resolveRedirectUrl(currentUrl, location) ?: return null
        return validatePreviewTarget(resolvedUrl.toString())
    }

    private suspend fun validatePreviewTarget(url: String): Url? {
        val parsedUrl = try {
            Url(url)
        } catch (_: Exception) {
            return null
        }

        if (parsedUrl.protocol.name != HTTPS_PROTOCOL) return null

        val host = parsedUrl.host.trim().lowercase()
        if (host.isEmpty() || host == LOCALHOST || host.endsWith(LOCAL_DOMAIN_SUFFIX)) {
            return null
        }

        val resolvedAddresses = resolveHostAddresses(host)
        if (resolvedAddresses.isEmpty()) return null
        if (resolvedAddresses.any { !isAllowedResolvedAddress(it) }) return null

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

    /**
     * Rejects resolved destinations that fall into special-use address space which must never be
     * contacted by the link-preview fetcher.
     *
     * Blocked categories:
     * - loopback
     * - private/internal
     * - link-local
     * - multicast
     * - unspecified
     * - documentation / benchmark / other reserved special-use ranges
     *
     * References:
     * RFC 1122, RFC 1918, RFC 3927, RFC 4193, RFC 4291, RFC 5737, RFC 3849, RFC 6598,
     * RFC 6890, RFC 7526, plus the IANA IPv4/IPv6 Special-Purpose Address Registries.
     */
    private fun isAllowedResolvedAddress(address: String): Boolean {
        val normalized = address.substringBefore('%')
        return when {
            normalized.contains('.') -> isAllowedIpv4Address(normalized)
            normalized.contains(':') -> isAllowedIpv6Address(normalized)
            else -> false
        }
    }

    /**
     * IPv4 filter for special-use ranges that must not be reachable from preview fetching.
     *
     * Rejected ranges currently include:
     * - `0.0.0.0/8` unspecified (RFC 1122)
     * - `10.0.0.0/8`, `172.16.0.0/12`, `192.168.0.0/16` private-use (RFC 1918)
     * - `100.64.0.0/10` shared carrier-grade NAT space (RFC 6598)
     * - `127.0.0.0/8` loopback (RFC 1122)
     * - `169.254.0.0/16` link-local (RFC 3927)
     * - `192.0.0.0/24` IETF protocol assignments (RFC 6890)
     * - `192.0.2.0/24`, `198.51.100.0/24`, `203.0.113.0/24` documentation/test (RFC 5737)
     * - `192.88.99.0/24` deprecated 6to4 relay anycast (RFC 7526)
     * - `198.18.0.0/15` benchmark testing (RFC 2544)
     * - `224.0.0.0/4` multicast and higher special-use space
     */
    private fun isAllowedIpv4Address(address: String): Boolean {
        val octets = address.split('.')
        if (octets.size != IPV4_OCTET_COUNT) return false

        val values = octets.map { it.toIntOrNull() ?: return false }
        if (values.any { it !in IPV4_OCTET_RANGE }) return false

        val first = values[0]
        val second = values[1]
        val third = values[2]

        return when (first) {
            0 -> false
            10 -> false
            100 if second in 64..127 -> false
            127 -> false
            169 if second == 254 -> false
            172 if second in 16..31 -> false
            192 if second == 0 && third == 0 -> false
            192 if second == 0 && third == 2 -> false
            192 if second == 88 && third == 99 -> false
            192 if second == 168 -> false
            198 if second in 18..19 -> false
            198 if second == 51 && third == 100 -> false
            203 if second == 0 && third == 113 -> false
            in 224..255 -> false
            else -> true
        }
    }

    /**
     * IPv6 filter for special-use ranges that must not be reachable from preview fetching.
     *
     * Rejected ranges currently include:
     * - `::/128` unspecified (RFC 4291)
     * - `::1/128` loopback (RFC 4291)
     * - `fe80::/10` link-local unicast (RFC 4291)
     * - `fc00::/7` unique local / private-use (RFC 4193)
     * - `ff00::/8` multicast (RFC 4291)
     * - `2001:db8::/32` documentation/examples (RFC 3849)
     * - IPv4-mapped IPv6 addresses are delegated to the IPv4 filter
     */
    private fun isAllowedIpv6Address(address: String): Boolean {
        val normalized = address.lowercase()
        if (normalized == "::" || normalized == "::1") return false
        if (normalized.startsWith("ff")) return false
        if (normalized.startsWith("fe8") ||
            normalized.startsWith("fe9") ||
            normalized.startsWith("fea") ||
            normalized.startsWith("feb")
        ) {
            return false
        }
        if (normalized.startsWith("fc") || normalized.startsWith("fd")) return false
        if (normalized.startsWith("fec") || normalized.startsWith("fed")) return false
        if (normalized.startsWith("2001:db8")) return false

        val ipv4MappedPrefix = "::ffff:"
        if (normalized.startsWith(ipv4MappedPrefix)) {
            return isAllowedIpv4Address(normalized.removePrefix(ipv4MappedPrefix))
        }

        return true
    }

    private sealed interface PreviewRequestOutcome<out T> {
        data class Success<T>(val value: T?) : PreviewRequestOutcome<T>
        data class Redirect(val url: Url) : PreviewRequestOutcome<Nothing>
        data object Failure : PreviewRequestOutcome<Nothing>
    }

    private companion object {
        const val HTTPS_PROTOCOL = "https"
        const val LOCALHOST = "localhost"
        const val LOCAL_DOMAIN_SUFFIX = ".local"
        const val MAX_PREVIEW_REDIRECT_HOPS = 3
        const val MAX_PREVIEW_METADATA_BYTES = 64 * 1024
        const val MAX_PREVIEW_IMAGE_BYTES = 5 * 1024 * 1024
        const val PREVIEW_READ_BUFFER_SIZE = 8 * 1024
        const val HTTP_ERROR_STATUS_CODE = 400
        val HTTP_REDIRECT_STATUS_RANGE = 300..399
        const val HTML_HEAD_START_MARKER = "<head"
        const val HTML_HEAD_END_MARKER = "</head>"
        const val IPV4_OCTET_COUNT = 4
        val IPV4_OCTET_RANGE = 0..255
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
