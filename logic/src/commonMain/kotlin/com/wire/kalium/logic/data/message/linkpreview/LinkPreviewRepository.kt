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
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders

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
    suspend fun fetchImage(imageUrl: String): Either<CoreFailure, ByteArray?>
}

/**
 * Default implementation of LinkPreviewRepository.
 */
internal class LinkPreviewRepositoryImpl(
    private val httpClient: HttpClient
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

    override suspend fun fetchImage(imageUrl: String): Either<CoreFailure, ByteArray?> {
        // MVP: Return null for images; implement later with image fetching
        return Either.Right(null)
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
}
