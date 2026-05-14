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
package com.wire.kalium.cells.domain.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.cells.data.FileFilters
import com.wire.kalium.cells.data.SortingCriteria
import com.wire.kalium.cells.data.SortingSpec
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.fold

@Suppress("LongParameterList")
internal class FilePagingSource(
    val query: String,
    val conversationId: String?,
    val pageSize: Int,
    val getPaginatedNodesUseCase: GetPaginatedNodesUseCase,
    val fileFilters: FileFilters,
    val sortingSpec: SortingSpec = SortingSpec(
        criteria = SortingCriteria.FOLDERS_FIRST_THEN_ALPHABETICAL,
        descending = true
    ),
) : PagingSource<Int, Node>() {

    private val nodeUuids = mutableSetOf<String>()

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Node> =
        getPaginatedNodesUseCase(
            conversationId = conversationId,
            query = query,
            limit = pageSize,
            offset = params.key ?: 0,
            fileFilters = fileFilters,
            sortingSpec = sortingSpec,
        ).fold(
            { error ->
                LoadResult.Error(
                    throwable = FileListLoadError(error is NetworkFailure.NoNetworkConnection)
                )
            },
            { files ->
                LoadResult.Page(
                    data = files.data.removeDuplicates(),
                    prevKey = null,
                    nextKey = files.pagination?.nextOffset
                )
            }
        )

    override fun getRefreshKey(state: PagingState<Int, Node>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }

    private fun List<Node>.removeDuplicates(): List<Node> {
        val uniqueNodes = mutableListOf<Node>()
        for (node in this) {
            if (node.uuid !in nodeUuids) {
                nodeUuids.add(node.uuid)
                uniqueNodes.add(node)
            }
        }
        return uniqueNodes
    }
}

public data class FileListLoadError(val isConnectionError: Boolean) : Throwable()
