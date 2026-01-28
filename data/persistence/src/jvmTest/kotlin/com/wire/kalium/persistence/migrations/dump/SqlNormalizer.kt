/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.persistence.migrations.dump

object SqlNormalizer {
    private val blockComment = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val lineComment = Regex("--.*$", RegexOption.MULTILINE)
    private val quotedIdentifier = Regex("\"([^\"]*)\"")
    private val multipleSpaces = Regex("\\s+")
    private val commaSpaces = Regex("\\s*,\\s*")
    private val openParenSpaces = Regex("\\s*\\(\\s*")
    private val closeParenSpaces = Regex("\\s*\\)\\s*")

    fun normalize(input: String?): String {
        if (input.isNullOrBlank()) return ""

        return input
            .replace(blockComment, "")
            .replace(lineComment, "")
            .replace(quotedIdentifier, "$1")
            .replace(multipleSpaces, " ")
            .replace(commaSpaces, ", ")
            .replace(openParenSpaces, " (")
            .replace(closeParenSpaces, ") ")
            .trim()
    }
}
