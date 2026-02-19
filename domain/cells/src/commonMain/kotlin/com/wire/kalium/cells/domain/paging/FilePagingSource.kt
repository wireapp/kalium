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

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultError
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import com.wire.kalium.cells.data.MIMEType
import com.wire.kalium.cells.data.Sorting
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.usecase.GetPaginatedNodesUseCase
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.fold

internal class FilePagingSource(
    val query: String,
    val conversationId: String?,
    val pageSize: Int,
    val getPaginatedNodesUseCase: GetPaginatedNodesUseCase,
    val onlyDeleted: Boolean = false,
    val tags: List<String> = emptyList(),
    val owners: List<String> = emptyList(),
    val mimeTypes: List<MIMEType> = emptyList(),
    val sorting: Sorting = Sorting.FOLDERS_FIRST_THEN_ALPHABETICAL,
    val sortDescending: Boolean = true,
) : PagingSource<Int, Node>() {

    private val nodeUuids = mutableSetOf<String>()

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, Node> =
        getPaginatedNodesUseCase(
            conversationId = conversationId,
            query = query,
            limit = pageSize,
            offset = params.key ?: 0,
            onlyDeleted = onlyDeleted,
            tags = tags,
            owners = owners,
            mimeTypes = mimeTypes,
            sorting = sorting,
            sortDescending = sortDescending
        ).fold(
            { error ->
                PagingSourceLoadResultError<Int, Node>(
                    throwable = FileListLoadError(error is NetworkFailure.NoNetworkConnection)
                ) as PagingSourceLoadResult<Int, Node>
            },
            { files ->
                PagingSourceLoadResultPage(
                    data = files.data.removeDuplicates(),
                    prevKey = null,
                    nextKey = files.pagination?.nextOffset
                ) as PagingSourceLoadResult<Int, Node>
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
