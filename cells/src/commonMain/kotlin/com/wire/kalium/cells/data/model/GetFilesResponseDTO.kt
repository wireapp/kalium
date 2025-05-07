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
package com.wire.kalium.cells.data.model

import com.wire.kalium.cells.sdk.kmp.model.RestNodeCollection

internal data class GetFilesResponseDTO(
    val nodes: List<CellNodeDTO>,
    val pagination: PaginationDTO? = null,
)

internal data class PaginationDTO(
    val limit: Int,
    val total: Int,
    val currentPage: Int,
    val totalPages: Int,
    val nextOffset: Int?,
)

internal fun RestNodeCollection.toDto() = GetFilesResponseDTO(
    nodes = nodes?.map { node ->
        node.toDto()
    } ?: emptyList(),
    pagination = pagination?.let {
        PaginationDTO(
            limit = it.limit ?: 0,
            total = it.total ?: 0,
            currentPage = it.currentPage ?: 0,
            totalPages = it.totalPages ?: 0,
            nextOffset = it.nextOffset,
        )
    }
)
