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
package com.wire.kalium.logic.data.cells

import com.wire.kalium.network.api.unbound.cells.CellNodeDTO

data class CellNode(
    val uuid: String,
    val versionId: String,
    val path: String,
    val eTag: String? = null,
    val type: String? = null,
    val isRecycleBin: Boolean = false,
    val isDraft: Boolean = false,
)

fun CellNodeDTO.toModel() = CellNode(
    uuid = uuid,
    versionId = versionId,
    path = path,
    eTag = eTag,
    type = type,
    isRecycleBin = isRecycleBin,
    isDraft = isDraft,
)

fun CellNode.toDto() = CellNodeDTO(
    uuid = uuid,
    versionId = versionId,
    path = path,
    eTag = eTag,
    type = type,
    isRecycleBin = isRecycleBin,
    isDraft = isDraft,
)
