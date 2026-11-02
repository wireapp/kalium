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

import io.ktor.http.URLProtocol
import io.ktor.http.Url

/** Extracts complete URL candidates with original UTF-16 spans. Fetching enforces network policy separately. */
internal object UrlDetector {
    private const val MAX_SCAN_CHARS = 8_000
    private const val MAX_HOST_CHARS = 253
    private const val MAX_PORT = 65535
    private const val OPENING_BRACKETS = "([{"
    private const val CLOSING_BRACKETS = ")]}"
    private const val SENTENCE_PUNCTUATION = ".,;:!?"

    // Smart punctuation (on by default on iOS) turns typed quotes into these.
    private const val TYPOGRAPHIC_OPENING_QUOTES = "\u201C\u2018\u201E\u00AB"
    private const val TYPOGRAPHIC_CLOSING_QUOTES = "\u201D\u2019\u201C\u00BB"
    private val prefixes = listOf("https://", "http://", "www.")

    data class UrlMatch(val url: String, val start: Int, val end: Int)

    fun detect(text: String, excluded: List<IntRange> = emptyList()): List<UrlMatch> {
        val matches = mutableListOf<UrlMatch>()
        val limit = minOf(text.length, MAX_SCAN_CHARS)
        var start = 0
        while (start < limit) {
            val prefix = prefixes.firstOrNull { text.startsWith(it, start, ignoreCase = true) }
            if (prefix != null && hasStartBoundary(text, start)) {
                var end = start + prefix.length
                while (end < limit && !isDelimiter(text[end])) end++
                if (!endsWithinBudget(text, end, limit)) break
                val authorityOffset = if (prefix == "www.") 0 else prefix.length
                val trimmedEnd = trimPunctuation(text, start, end, authorityOffset)
                val candidate = text.substring(start, trimmedEnd)
                if (isCompleteUrl(candidate, authorityOffset) &&
                    excluded.none { start <= it.last && it.first < trimmedEnd }
                ) {
                    matches.add(UrlMatch(candidate, start, trimmedEnd))
                }
                // Invalid candidates are skipped whole, including embedded URL-looking text.
                start = end
            } else {
                start++
            }
        }
        return matches
    }

    private fun endsWithinBudget(text: String, end: Int, limit: Int): Boolean {
        if (end > limit) return false
        // Never turn an unfinished candidate at the scan limit into a shorter destination.
        return end < limit || limit == text.length || isDelimiter(text[limit])
    }

    private fun hasStartBoundary(text: String, start: Int): Boolean =
        start == 0 || text[start - 1].isWhitespace() || text[start - 1] in "([{<\"'*_~$TYPOGRAPHIC_OPENING_QUOTES"

    private fun isDelimiter(character: Char): Boolean = character.isWhitespace() || character in "<>\""

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun isCompleteUrl(candidate: String, authorityStart: Int): Boolean {
        if (candidate.any { it < ' ' || it == '\u007f' }) return false
        val authority = candidate.substring(authorityStart).takeWhile { it !in "/?#" }
        if (authority.isEmpty() || authority.any { !it.isAsciiAuthorityCharacter() }) return false
        val parsed = try {
            Url(if (authorityStart == 0) "https://$candidate" else candidate)
        } catch (_: Exception) {
            return false
        }
        val host = parsed.host
        if (parsed.protocol !in listOf(URLProtocol.HTTP, URLProtocol.HTTPS) || parsed.user != null || parsed.password != null) return false
        if (host.isEmpty() || host.length > MAX_HOST_CHARS || !authority.startsWith(host, ignoreCase = true)) return false
        if (!hasUnambiguousHost(host)) return false
        return hasValidPort(authority, parsed)
    }

    private fun hasValidPort(authority: String, parsed: Url): Boolean {
        val suffix = authority.substring(parsed.host.length)
        return if (suffix.isEmpty()) {
            parsed.specifiedPort == 0 // Ktor uses zero when no port was specified.
        } else {
            val digits = suffix.drop(1)
            val port = digits.takeIf { it.all { character -> character in '0'..'9' } }?.toIntOrNull()
            suffix.startsWith(':') && port?.let { it in 1..MAX_PORT && it == parsed.specifiedPort } == true
        }
    }

    private fun Char.isAsciiAuthorityCharacter(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9' || this in ".-:[]"

    private fun hasUnambiguousHost(host: String): Boolean = if (':' in host) {
        host.startsWith('[') && host.endsWith(']') && host.count { it == '[' } == 1 && host.count { it == ']' } == 1
    } else {
        '[' !in host && ']' !in host && '.' in host
    }

    private fun trimPunctuation(text: String, start: Int, end: Int, authorityOffset: Int): Int {
        val hasResource = (start + authorityOffset until end).any { text[it] in "/?#" }
        val balance = IntArray(OPENING_BRACKETS.length)
        for (index in start until end) {
            val opening = OPENING_BRACKETS.indexOf(text[index])
            val closing = CLOSING_BRACKETS.indexOf(text[index])
            if (opening >= 0) balance[opening]++
            if (closing >= 0) balance[closing]--
        }
        var trimmedEnd = end
        while (trimmedEnd > start) {
            val last = text[trimmedEnd - 1]
            when {
                last in SENTENCE_PUNCTUATION -> {
                    var punctuationStart = trimmedEnd - 1
                    while (punctuationStart > start && text[punctuationStart - 1] in SENTENCE_PUNCTUATION) punctuationStart--
                    // Path, query, and fragment punctuation belongs to the URL unless it follows a closing prose wrapper.
                    if (hasResource && !isClosingWrapper(text, start, punctuationStart - 1, balance)) break
                    trimmedEnd = punctuationStart
                }
                isClosingWrapper(text, start, trimmedEnd - 1, balance) -> {
                    trimmedEnd--
                    val closing = CLOSING_BRACKETS.indexOf(last)
                    if (closing >= 0) balance[closing]++
                }
                else -> return trimmedEnd
            }
        }
        return trimmedEnd
    }

    private fun isClosingWrapper(text: String, start: Int, index: Int, balance: IntArray): Boolean {
        if (index < start) return false
        val character = text[index]
        val closing = CLOSING_BRACKETS.indexOf(character)
        return character in TYPOGRAPHIC_CLOSING_QUOTES ||
            (character in "*_~'" && start > 0 && text[start - 1] == character) ||
            (closing >= 0 && balance[closing] < 0)
    }
}
