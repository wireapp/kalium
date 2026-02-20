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

import com.wire.kalium.cells.domain.model.CellNodeType

public data class FileFilters(
    val onlyDeleted: Boolean = false,
    val nodeType: CellNodeType = CellNodeType.ALL,
    val tags: List<String> = emptyList(),
    val owners: List<String> = emptyList(),
    val mimeTypes: List<MIMEType> = emptyList(),
    val hasPublicLink: Boolean? = null,
)
