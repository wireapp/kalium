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

internal const val MAX_PREVIEW_TITLE_LENGTH = 300
internal const val MAX_PREVIEW_DESCRIPTION_LENGTH = 1_000
internal const val MAX_PREVIEW_URL_LENGTH = 2_048
internal const val MAX_PREVIEW_SITE_NAME_LENGTH = 120
internal const val MAX_PREVIEW_ASSET_NAME_LENGTH = 255

internal expect fun normalizePreviewUnicode(input: String): String

internal fun sanitizePreviewText(input: String?, maxLength: Int, collapseWhitespace: Boolean = true): String? {
    val value = input ?: return null
    val normalized = normalizePreviewUnicode(value)
        .map { character ->
            when {
                character == '\u0000' -> ' '
                character == '\uFFFD' -> ' '
                character.isUnsafeControlCharacter() -> ' '
                else -> character
            }
        }
        .joinToString("")

    val whitespaceSafe = if (collapseWhitespace) {
        normalized
            .replace(Regex("""\s+"""), " ")
            .trim()
    } else {
        normalized.trim()
    }

    val truncated = whitespaceSafe.take(maxLength)
    return truncated.takeIf { it.isNotEmpty() }
}

@Suppress("MagicNumber")
private fun Char.isUnsafeControlCharacter(): Boolean {
    if (this == '\n' || this == '\r' || this == '\t') return false
    return code in 0x00..0x1F || code in 0x7F..0x9F
}
