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

/**
 * Scans HTML head for Open Graph metadata.
 *
 * Uses simple regex-based parsing (temporary MVP implementation).
 * TODO: Replace with Ksoup when ADR is approved.
 */
internal object OpenGraphScanner {
    private val metaPropertyRegex = Regex("""<meta\s+property=["']([^"']+)["']\s+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val metaNameRegex = Regex("""<meta\s+name=["']([^"']+)["']\s+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
    private val titleRegex = Regex("""<title>([^<]*)</title>""", RegexOption.IGNORE_CASE)

    private val OG_KEYS = setOf(
        "og:title",
        "og:type",
        "og:image",
        "og:url",
        "og:description",
        "og:site_name",
        "og:image:user_generated"
    )

    /**
     * Parses HTML head section and extracts Open Graph data.
     *
     * @param htmlHead The HTML head section (can be partial or full).
     * @param originalUrl The original URL (used as fallback for og:url).
     * @return OpenGraphData with extracted metadata, or null if critical fields are missing.
     */
    fun scan(htmlHead: String, originalUrl: String): OpenGraphData? {
        val ogData = mutableMapOf<String, String>()
        val imageUrls = mutableListOf<String>()
        var pageTitle: String? = null

        // Extract meta tags with property attribute
        metaPropertyRegex.findAll(htmlHead).forEach { match ->
            val property = match.groupValues[1]
            val content = match.groupValues[2].unescapeHtml()

            when {
                property == "og:image" && content.isNotEmpty() -> imageUrls.add(content)
                property in OG_KEYS && content.isNotEmpty() -> ogData[property] = content
            }
        }

        // Extract title from <title> tag as fallback
        titleRegex.find(htmlHead)?.let { match ->
            pageTitle = match.groupValues[1].unescapeHtml()
        }

        // Extract title from meta description as fallback
        metaNameRegex.findAll(htmlHead).forEach { match ->
            val name = match.groupValues[1].lowercase()
            val content = match.groupValues[2].unescapeHtml()
            if (name == "description" && "og:description" !in ogData) {
                ogData["og:description"] = content
            }
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
            imageUrls = imageUrls,
            siteName = siteName
        )
    }

    private fun String.unescapeHtml(): String {
        return this
            .replace("&quot;", "\"")
            .replace("&#34;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&nbsp;", " ")
    }
}
