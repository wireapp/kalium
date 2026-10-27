/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */

package com.wire.kalium.network.api.base.authenticated

import com.wire.kalium.network.api.authenticated.giphy.GiphyGifsResponseDTO
import com.wire.kalium.network.api.authenticated.giphy.GiphySearchSuggestionsResponseDTO
import com.wire.kalium.network.utils.NetworkResponse
import okio.Sink

/**
 * Giphy through the backend's optional `proxy` service, which adds Wire's Giphy API key.
 * Backends without that service answer 404.
 */
interface GiphyApi {
    suspend fun search(
        query: String,
        limit: Int,
        offset: Int,
        rating: String,
        language: String?,
    ): NetworkResponse<GiphyGifsResponseDTO>

    suspend fun trending(limit: Int, offset: Int, rating: String): NetworkResponse<GiphyGifsResponseDTO>

    suspend fun searchSuggestions(query: String): NetworkResponse<GiphySearchSuggestionsResponseDTO>

    /**
     * Downloads a GIF from Giphy's CDN into [sink], without Wire credentials.
     * Fails unless [url] and the final address after redirects are HTTPS `giphy.com` addresses,
     * and stops reading once the body exceeds [maxSizeInBytes].
     * @return the number of bytes written
     */
    suspend fun downloadGif(url: String, maxSizeInBytes: Long, sink: Sink): NetworkResponse<Long>
}
