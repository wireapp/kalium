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

import com.wire.kalium.logic.data.message.mention.MessageMention

/**
 * Computes ranges to exclude from link preview detection (mentions and markdown links).
 */
internal object ExclusionRanges {
    /**
     * Computes a list of UTF-16 ranges that should be excluded from link preview detection.
     *
     * @param text The message text.
     * @param mentions List of mentions to exclude.
     * @return List of IntRange(s) covering mentioned text and markdown link URLs.
     */
    fun compute(text: String, mentions: List<MessageMention> = emptyList()): List<IntRange> {
        val ranges = mutableListOf<IntRange>()

        // Add mention ranges
        mentions.forEach { mention ->
            ranges.add(mention.start until (mention.start + mention.length))
        }

        // Add markdown link URL ranges: [text](url) — match the whole bracket+paren
        val markdownLinkRegex = Regex("""\[.+?\]\((.+?)\)""")
        markdownLinkRegex.findAll(text).forEach { match ->
            ranges.add(match.range)
        }

        return ranges
    }
}
