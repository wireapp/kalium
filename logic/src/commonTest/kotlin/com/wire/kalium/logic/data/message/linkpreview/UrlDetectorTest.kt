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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UrlDetectorTest {
    @Test
    fun givenClaudeArticle_whenDetecting_thenPreservesFullUrlAndOffsets() {
        val url = "https://claude.dev/blog/solving-code-review-at-anthropic/"
        val prefix = "Read this: "

        assertEquals(
            listOf(UrlDetector.UrlMatch(url, prefix.length, prefix.length + url.length)),
            UrlDetector.detect("$prefix$url please")
        )
    }

    @Test
    fun givenBbcNews_whenDetecting_thenPreservesFullUrl() {
        val url = "https://www.bbc.com/news"

        assertEquals(listOf(UrlDetector.UrlMatch(url, 0, url.length)), UrlDetector.detect(url))
    }

    @Test
    fun givenModernTopLevelDomains_whenDetecting_thenDoesNotTruncateHostOrPath() {
        val domains = listOf("dev", "app", "company", "technology", "info", "co.uk")
        val prefixes = listOf("https://", "www.")

        domains.forEach { domain ->
            prefixes.forEach { prefix ->
                val url = "${prefix}example.$domain/article?source=chat&view=full#details"

                assertEquals(listOf(UrlDetector.UrlMatch(url, 0, url.length)), UrlDetector.detect(url), url)
            }
        }
    }

    @Test
    fun givenUppercaseHostAndPort_whenDetecting_thenPreservesUrl() {
        val url = "https://EXAMPLE.DEV:8443/article"

        assertEquals(listOf(UrlDetector.UrlMatch(url, 0, url.length)), UrlDetector.detect(url))
    }

    @Test
    fun givenUnfamiliarDomainSuffix_whenDetecting_thenNeverReturnsPartialHost() {
        val suffixes = listOf("dev123", "dev-foo", "com.123", "d", "a".repeat(64))

        suffixes.forEach { suffix ->
            listOf("https://", "www.").forEach { prefix ->
                val url = "${prefix}example.$suffix/article"

                val matches = UrlDetector.detect(url)

                assertTrue(matches.isEmpty() || matches == listOf(UrlDetector.UrlMatch(url, 0, url.length)), url)
            }
        }
    }

    @Test
    fun givenSentenceEndingPeriod_whenDetecting_thenExcludesPeriod() {
        val url = "https://example.dev"

        assertEquals(listOf(UrlDetector.UrlMatch(url, 4, 4 + url.length)), UrlDetector.detect("See $url."))
    }

    @Test
    fun givenExcludedModernDomain_whenDetecting_thenReturnsNextUrlWithOriginalOffsets() {
        val excludedUrl = "https://claude.dev/blog/solving-code-review-at-anthropic/"
        val url = "https://www.bbc.com/news"
        val text = "$excludedUrl $url"
        val start = excludedUrl.length + 1

        assertEquals(
            listOf(UrlDetector.UrlMatch(url, start, text.length)),
            UrlDetector.detect(text, listOf(0 until excludedUrl.length))
        )
    }

    @Test
    fun givenEmojiBeforeModernDomain_whenDetecting_thenUsesUtf16Offsets() {
        val prefix = "\uD83D\uDCAC "
        val url = "https://claude.dev/blog/solving-code-review-at-anthropic/"

        assertEquals(
            listOf(UrlDetector.UrlMatch(url, prefix.length, prefix.length + url.length)),
            UrlDetector.detect("$prefix$url")
        )
    }

    @Test
    fun givenAmbiguousAuthority_whenDetecting_thenRejectsWholeCandidateAndFindsNextLink() {
        val invalidCandidates = listOf(
            "https://example.com@attacker.example/path",
            "https://example.com%2eattacker.example/path",
            "https://example.comévil/path",
            "https://example.com\\attacker.example/path",
            "https://example.com_evil/path",
            "https://example.com@attacker.example/(https://inner.example/path)"
        )
        val nextUrl = "https://safe.example/article"

        invalidCandidates.forEach { candidate ->
            val text = "$candidate $nextUrl"

            assertEquals(
                listOf(UrlDetector.UrlMatch(nextUrl, candidate.length + 1, text.length)),
                UrlDetector.detect(text),
                candidate
            )
        }
    }

    @Test
    fun givenLongOrBudgetTruncatedCandidate_whenDetecting_thenRejectsWithoutPartialMatch() {
        val hostPrefix = "https://example.com"
        val crossingBudget = " ".repeat(8_000 - hostPrefix.length) + "$hostPrefix.attacker.example/path"
        val candidates = listOf("https://" + "a.".repeat(2_000) + "x", crossingBudget)

        candidates.forEach { text -> assertTrue(UrlDetector.detect(text).isEmpty()) }
    }

    @Test
    fun givenSurroundingPunctuation_whenDetecting_thenPreservesBalancedUrlAndUtf16Offsets() {
        val cases = listOf(
            Triple("\uD83D\uDCAC (", "https://example.dev/path_(details)", ")."),
            Triple("\uD83D\uDCAC [", "https://example.dev/query?value=[one]", "]!"),
            Triple("**", "https://example.dev/path", "**"),
            Triple("'", "https://example.dev", "'")
        )

        cases.forEach { (prefix, url, suffix) ->
            assertEquals(
                listOf(UrlDetector.UrlMatch(url, prefix.length, prefix.length + url.length)),
                UrlDetector.detect("$prefix$url$suffix")
            )
        }
    }
}
