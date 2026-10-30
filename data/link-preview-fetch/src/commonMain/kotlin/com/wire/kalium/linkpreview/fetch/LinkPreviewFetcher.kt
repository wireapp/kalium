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

package com.wire.kalium.linkpreview.fetch

import com.wire.kalium.util.InternalKaliumApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okio.Buffer
import kotlin.coroutines.cancellation.CancellationException

/** Fetches untrusted preview URLs with fixed public-address, redirect, time, and byte limits. */
@InternalKaliumApi
public interface LinkPreviewFetcher {
    public suspend fun fetchHtmlHead(url: String): String?
    public suspend fun fetchImage(url: String): FetchedPreviewImage?

    /** Cancels active fetches and awaits their cleanup. Later fetches return null. */
    public suspend fun close()
}

/** Creates an isolated fetcher. No transport is initialized until its first fetch. */
@InternalKaliumApi
public fun createLinkPreviewFetcher(): LinkPreviewFetcher = LinkPreviewFetcherImpl()

@Suppress("TooGenericExceptionCaught", "MagicNumber", "CyclomaticComplexMethod", "ComplexCondition")
internal class LinkPreviewFetcherImpl(
    private val createClient: () -> HttpClient = ::createPreviewHttpClient
) : LinkPreviewFetcher {
    private val lifecycleMutex = Mutex()
    private var closed = false
    private var client: HttpClient? = null
    private val requests = mutableSetOf<PreviewFetchRequest>()
    private val closeFinished = CompletableDeferred<Unit>()

    // Initialization, registration, and closure share a lock: shutdown cannot miss a new request
    // or allow a late caller to create another client. Request jobs also inherit caller cancellation.
    private suspend fun <T> fetch(block: suspend (HttpClient) -> T?): T? {
        var request: PreviewFetchRequest? = null
        return try {
            val callerJob = currentCoroutineContext()[Job]
            val active = lifecycleMutex.withLock {
                if (closed) return null
                val httpClient = client ?: createClient().also { client = it }
                PreviewFetchRequest(httpClient, SupervisorJob(callerJob)).also { requests.add(it) }
            }
            request = active
            withContext(active.job) {
                withTimeoutOrNull(MAX_PREVIEW_FETCH_MILLIS) { block(active.client) }
            }
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            null
        } finally {
            request?.let { active ->
                withContext(NonCancellable) {
                    active.job.complete()
                    active.job.join()
                    lifecycleMutex.withLock {
                        requests.remove(active)
                        active.finished.complete(Unit)
                    }
                }
            }
        }
    }

    override suspend fun close(): Unit = withContext(NonCancellable) {
        val shutdown = lifecycleMutex.withLock {
            if (closed) null else {
                closed = true
                PreviewFetchShutdown(client, requests.toList())
            }
        }
        if (shutdown == null) {
            closeFinished.await()
        } else {
            try {
                shutdown.requests.forEach { it.job.cancel() }
                shutdown.client?.coroutineContext?.job?.cancel()
                shutdown.client?.engine?.coroutineContext?.job?.cancel()
                try {
                    try {
                        shutdown.client?.close()
                    } finally {
                        shutdown.client?.engine?.close()
                    }
                } finally {
                    shutdown.requests.forEach { it.finished.await() }
                    shutdown.client?.coroutineContext?.job?.join()
                    shutdown.client?.engine?.coroutineContext?.job?.join()
                }
                closeFinished.complete(Unit)
            } catch (failure: Throwable) {
                closeFinished.completeExceptionally(failure)
                throw failure
            }
        }
    }

    private class PreviewFetchRequest(val client: HttpClient, val job: CompletableJob) {
        val finished = CompletableDeferred<Unit>()
    }

    private class PreviewFetchShutdown(val client: HttpClient?, val requests: List<PreviewFetchRequest>)

    override suspend fun fetchHtmlHead(url: String): String? = fetch { client ->
        executeGetWithRedirects(client, url) { resp ->
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

    override suspend fun fetchImage(imageUrl: String): FetchedPreviewImage? = fetch { client ->
        executeGetWithRedirects(client, imageUrl) { resp ->
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

            FetchedPreviewImage(
                bytes = bytes,
                mimeType = mimeType
            )
        }
    }

    private suspend fun <T> executeGetWithRedirects(
        httpClient: HttpClient,
        initialUrl: String,
        onSuccess: suspend (HttpResponse) -> T?
    ): T? {
        val validatedInitialUrl = validatePreviewTarget(initialUrl) ?: return null
        return executeGetRequest(
            httpClient = httpClient,
            currentUrl = validatedInitialUrl,
            visitedUrls = mutableSetOf(validatedInitialUrl.toString()),
            redirectsRemaining = MAX_PREVIEW_REDIRECTS,
            onSuccess = onSuccess
        )
    }

    private suspend fun <T> executeGetRequest(
        httpClient: HttpClient,
        currentUrl: Url,
        visitedUrls: MutableSet<String>,
        redirectsRemaining: Int,
        onSuccess: suspend (HttpResponse) -> T?
    ): T? {
        var requestExecution: Job? = null
        val outcome = try {
            httpClient.prepareGet(currentUrl.toString()) {
                requestExecution = executionContext
            }.execute { response ->
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
        } finally {
            // Ktor's request execution job is independent of the awaiting caller. Cancelling
            // the caller alone can return before a native engine handler finishes releasing resources.
            withContext(NonCancellable) { requestExecution?.cancelAndJoin() }
        }

        return when (outcome) {
            is PreviewRequestOutcome.Success -> outcome.value
            is PreviewRequestOutcome.Redirect -> {
                val redirectUrl = outcome.url.takeIf { visitedUrls.add(it.toString()) } ?: return null
                executeGetRequest(
                    httpClient = httpClient,
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

@InternalKaliumApi
public data class FetchedPreviewImage(
    public val bytes: ByteArray,
    public val mimeType: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as FetchedPreviewImage

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
