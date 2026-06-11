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

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document

/**
 * Scans HTML head for Open Graph metadata using DOM-based parsing (Ksoup).
 *
 * Handles various HTML formatting edge cases: attribute order, extra attributes,
 * self-closing tags, malformed heads, and HTML entity decoding.
 */
internal object OpenGraphScanner {

    /**
     * Parses HTML head section and extracts Open Graph data.
     *
     * @param htmlHead The HTML head section (can be partial or full).
     * @param originalUrl The original URL (used as fallback for og:url).
     * @return OpenGraphData with extracted metadata, or null if critical fields are missing.
     */
    fun scan(htmlHead: String, originalUrl: String): OpenGraphData? {
        return try {
            val document = Ksoup.parse(htmlHead)
            extractOpenGraphData(document, originalUrl)
        } catch (_: Exception) {
            null
        }
    }

    private fun extractOpenGraphData(document: Document, originalUrl: String): OpenGraphData? {
        val ogData = mutableMapOf<String, String>()
        var firstImageUrl: String? = null
        var pageTitle: String? = null

        // Extract all meta tags
        document.select("meta").forEach { metaTag ->
            val property = metaTag.attr("property").takeIf { it.isNotEmpty() }
            val name = metaTag.attr("name").takeIf { it.isNotEmpty() }
            val content = metaTag.attr("content").trim()

            when {
                property != null && content.isNotEmpty() -> {
                    when (property) {
                        "og:image" -> {
                            if (firstImageUrl == null) firstImageUrl = content
                        }
                        else -> ogData[property] = content
                    }
                }
                name != null && content.isNotEmpty() -> {
                    val lowerName = name.lowercase()
                    if (lowerName == "description" && "og:description" !in ogData) {
                        ogData["og:description"] = content
                    }
                }
            }
        }

        // Extract page title as fallback
        document.selectFirst("title")?.let { titleTag ->
            pageTitle = titleTag.text().trim().takeIf { it.isNotEmpty() }
        }

        // Determine final values with fallbacks
        val title = ogData["og:title"] ?: pageTitle
        val url = ogData["og:url"] ?: originalUrl
        val description = ogData["og:description"]
        val siteName = ogData["og:site_name"]
        val type = ogData["og:type"] ?: "website"

        // Require title and URL
        if (title.isNullOrBlank() || url.isBlank()) return null

        return OpenGraphData(
            title = title,
            type = type,
            url = url,
            description = description,
            imageUrls = firstImageUrl?.let { listOf(it) } ?: emptyList(),
            siteName = siteName
        )
    }
}
