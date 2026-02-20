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

public enum class SortingCriteria(public val apiValue: String) {
    FOLDERS_FIRST_THEN_ALPHABETICAL("natural"),
    NAME_CASE_SENSITIVE("name"),
    NAME_CASE_INSENSITIVE("name_ci"),
    SIZE("size"),
    MODIFICATION_TIME("mtime"),
}

public data class SortingSpec(
    val criteria: SortingCriteria,
    val descending: Boolean = true
)
