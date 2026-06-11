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
import com.fleeksoft.ksoup.nodes.Element

/**
 * Scans HTML head for Open Graph metadata using DOM-based parsing (Ksoup).
 *
 * Handles various HTML formatting edge cases: attribute order, extra attributes,
 * self-closing tags, malformed heads, and HTML entity decoding.
 */
internal object OpenGraphScanner {

    private const val MAX_PREVIEW_TYPE_LENGTH = 64

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

        document.select("meta").forEach { metaTag ->
            processMetaTag(metaTag, ogData) { imageUrl ->
                if (firstImageUrl == null) firstImageUrl = imageUrl
            }
        }

        val pageTitle = extractPageTitle(document)
        return buildOpenGraphData(ogData, pageTitle, originalUrl, firstImageUrl)
    }

    private fun processMetaTag(metaTag: Element, ogData: MutableMap<String, String>, onImageFound: (String) -> Unit) {
        val property = metaTag.attr("property").takeIf { it.isNotEmpty() }
        val name = metaTag.attr("name").takeIf { it.isNotEmpty() }
        val content = metaTag.attr("content").trim()

        if (content.isEmpty()) return

        when {
            property == "og:image" -> onImageFound(content)
            property != null -> ogData[property] = content
            name != null && name.lowercase() == "description" && "og:description" !in ogData -> ogData["og:description"] = content
        }
    }

    private fun extractPageTitle(document: Document): String? =
        document.selectFirst("title")?.text()?.trim()?.takeIf { it.isNotEmpty() }

    private fun buildOpenGraphData(
        ogData: Map<String, String>,
        pageTitle: String?,
        originalUrl: String,
        firstImageUrl: String?
    ): OpenGraphData? {
        val title = sanitizePreviewText(
            ogData["og:title"] ?: pageTitle,
            MAX_PREVIEW_TITLE_LENGTH
        )
        val url = sanitizePreviewText(
            ogData["og:url"] ?: originalUrl,
            MAX_PREVIEW_URL_LENGTH,
            collapseWhitespace = false
        )
        val description = sanitizePreviewText(
            ogData["og:description"],
            MAX_PREVIEW_DESCRIPTION_LENGTH
        )
        val siteName = sanitizePreviewText(
            ogData["og:site_name"],
            MAX_PREVIEW_SITE_NAME_LENGTH
        )
        val type = sanitizePreviewText(
            ogData["og:type"],
            MAX_PREVIEW_TYPE_LENGTH,
            collapseWhitespace = false
        ) ?: "website"
        val imageUrl = sanitizePreviewText(
            firstImageUrl,
            MAX_PREVIEW_URL_LENGTH,
            collapseWhitespace = false
        )

        if (title.isNullOrBlank() || url?.isBlank() == true) return null

        return OpenGraphData(
            title = title,
            type = type,
            url = url,
            description = description,
            imageUrls = imageUrl?.let { listOf(it) } ?: emptyList(),
            siteName = siteName
        )
    }
}
