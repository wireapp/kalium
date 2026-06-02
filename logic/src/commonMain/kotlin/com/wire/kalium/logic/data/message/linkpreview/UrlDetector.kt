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
 * Detects URLs in text using the AOSP Patterns.WEB_URL regex, ported to commonMain for iOS parity.
 *
 * The regex pattern is vendored from Android Open Source Project (AOSP) to ensure byte-identical
 * detection results across Android and iOS platforms. See docs/adr/ for source URL and revision.
 *
 * Reference: android.util.Patterns.WEB_URL (Apache License 2.0)
 * https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/util/Patterns.java
 */
internal object UrlDetector {
    // AOSP android.util.Patterns.WEB_URL regex, vendored for cross-platform consistency.
    // This pattern matches:
    // - http/https/ftp schemes with domain names or IP addresses
    // - www. prefix with domain names or IP addresses
    // Supports IPv4, IPv6, and domain names with common TLDs.
    private val WEB_URL = Regex(
        "(?:(?:https?|ftp)://(?:(?:[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?\\.)+(?:com|org|net|edu|gov|mil|int|mobi|name|aero|asia|biz|cat|coop|info|jobs|museum|tel|travel|xxx|[a-z]{2})|(?:(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9]?[0-9])|(?:(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}))(?::[0-9]+)?(?:/[a-z0-9\\-._~:/?#\\[\\]@!\$&'()*+,;=]*)?)|(?:www\\.(?:[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?\\.)+(?:com|org|net|edu|gov|mil|int|mobi|name|aero|asia|biz|cat|coop|info|jobs|museum|tel|travel|xxx|[a-z]{2})|(?:(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9]?[0-9])\\.){3}(?:25[0-5]|2[0-4][0-9]|[0-1][0-9]{2}|[1-9]?[0-9])|(?:(?:[0-9a-f]{0,4}:){2,7}[0-9a-f]{0,4}))(?::[0-9]+)?(?:/[a-z0-9\\-._~:/?#\\[\\]@!\$&'()*+,;=]*)?",
        RegexOption.IGNORE_CASE
    )

    data class UrlMatch(val url: String, val start: Int, val end: Int)

    /**
     * Detects URLs in the given text, excluding ranges.
     *
     * @param text The text to scan for URLs.
     * @param excluded List of IntRanges to exclude (e.g., mentions, markdown links).
     *                 Matches overlapping any excluded range are dropped.
     * @return List of detected URLs with their UTF-16 string offsets, filtered by exclusion ranges.
     */
    fun detect(text: String, excluded: List<IntRange> = emptyList()): List<UrlMatch> {
        return WEB_URL.findAll(text)
            .map { UrlMatch(it.value, it.range.first, it.range.last + 1) }
            // Drop a match if it overlaps ANY excluded range
            .filter { m -> excluded.none { e -> m.start < e.last + 1 && e.first < m.end } }
            .toList()
    }
}
