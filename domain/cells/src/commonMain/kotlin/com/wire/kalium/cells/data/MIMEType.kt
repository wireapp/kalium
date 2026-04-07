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
package com.wire.kalium.cells.data

public enum class MIMEType(public val value: String) {
    PDF("application/pdf"),
    DOCUMENT("*word*"),
    IMAGE("image/*"),
    EXCEL("*spreadsheet*|*excel*"),
    PRESENTATION("*presentation*|*powerpoint*"),
    VIDEO("video/*"),
    AUDIO("audio/*"),
    ARCHIVE("application/zip|application/vnd.rar|application/x-7z-compressed|application/x-tar|application/gzip|application/x-bzip2"),
    TEXT("*text/plain*"),
}

public fun MIMEType.expandTerms(): List<String> =
    value.split("|").map { it.trim() }
