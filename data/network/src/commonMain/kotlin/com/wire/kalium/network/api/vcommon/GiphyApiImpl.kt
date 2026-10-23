/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network.api.vcommon

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.authenticated.giphy.GiphyGifsResponseDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphySearchSuggestionsResponseDTO
import com.wire.kalium.network.api.base.authenticated.GiphyApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.interceptUnsuccessfulResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.URLProtocol
import io.ktor.http.Url
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import okio.Sink
import okio.buffer
import okio.use
import kotlin.coroutines.cancellation.CancellationException

/**
 * The proxy route is the same on every API version, so one implementation serves all of them.
 * [mediaNetworkClient] must be unauthenticated: GIF files come from Giphy's CDN, never from the Wire backend.
 */
internal class GiphyApiImpl(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val mediaNetworkClient: UnboundNetworkClient,
) : GiphyApi {

    private val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        rating: String,
        language: String?,
    ): NetworkResponse<GiphyGifsResponseDTO> = wrapRequest {
        httpClient.get("$PATH_GIFS/$PATH_SEARCH") {
            parameter(QUERY_Q, query)
            parameter(QUERY_LIMIT, limit)
            parameter(QUERY_OFFSET, offset)
            parameter(QUERY_RATING, rating)
            language?.let { parameter(QUERY_LANG, it) }
        }
    }

    override suspend fun trending(limit: Int, offset: Int, rating: String): NetworkResponse<GiphyGifsResponseDTO> =
        wrapRequest {
            httpClient.get("$PATH_GIFS/$PATH_TRENDING") {
                parameter(QUERY_LIMIT, limit)
                parameter(QUERY_OFFSET, offset)
                parameter(QUERY_RATING, rating)
            }
        }

    override suspend fun searchSuggestions(query: String): NetworkResponse<GiphySearchSuggestionsResponseDTO> =
        wrapRequest {
            httpClient.get("$PATH_GIFS/$PATH_SEARCH/$PATH_TAGS") {
                parameter(QUERY_Q, query)
            }
        }

    override suspend fun downloadGif(url: String, maxSizeInBytes: Long, sink: Sink): NetworkResponse<Long> = try {
        require(Url(url).isGiphyHttps()) { "Not a Giphy HTTPS URL" }
        mediaNetworkClient.httpClient.prepareGet(url).execute { response ->
            // Checked again after redirects, so only a response from a Giphy address is written.
            require(response.call.request.url.isGiphyHttps()) { "Redirected away from Giphy" }
            if (response.status.isSuccess()) {
                writeLimited(response, maxSizeInBytes, sink)
            } else {
                interceptUnsuccessfulResponse(response)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        NetworkResponse.Error(KaliumException.GenericError(e))
    }

    private suspend fun writeLimited(response: HttpResponse, maxSizeInBytes: Long, sink: Sink): NetworkResponse<Long> {
        val declaredSize = response.contentLength()
        val written = if (declaredSize != null && declaredSize > maxSizeInBytes) {
            null
        } else {
            copyLimited(response.body<ByteReadChannel>(), maxSizeInBytes, sink)
        }
        return if (written == null) {
            NetworkResponse.Error(KaliumException.GenericError(IllegalStateException("GIF is larger than $maxSizeInBytes bytes")))
        } else {
            NetworkResponse.Success(written, response)
        }
    }

    /** @return the number of bytes written, or null once the body exceeds [maxSizeInBytes] */
    private suspend fun copyLimited(channel: ByteReadChannel, maxSizeInBytes: Long, sink: Sink): Long? {
        var written = 0L
        sink.buffer().use { bufferedSink ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (!channel.isClosedForRead && written <= maxSizeInBytes) {
                val read = channel.readAvailable(buffer, 0, buffer.size)
                if (read <= 0) break
                written += read
                if (written <= maxSizeInBytes) bufferedSink.write(buffer, 0, read)
            }
        }
        // A dropped connection also ends the loop; never report the partial file as a GIF.
        channel.closedCause?.let { throw it }
        return written.takeIf { it <= maxSizeInBytes }
    }

    private fun Url.isGiphyHttps(): Boolean =
        protocol == URLProtocol.HTTPS && (host == GIPHY_HOST || host.endsWith(".$GIPHY_HOST"))

    private companion object {
        const val PATH_GIFS = "proxy/giphy/v1/gifs"
        const val PATH_SEARCH = "search"
        const val PATH_TRENDING = "trending"
        const val PATH_TAGS = "tags"
        const val QUERY_Q = "q"
        const val QUERY_LIMIT = "limit"
        const val QUERY_OFFSET = "offset"
        const val QUERY_RATING = "rating"
        const val QUERY_LANG = "lang"
        const val BUFFER_SIZE = 8 * 1024
        const val GIPHY_HOST = "giphy.com"
    }
}
