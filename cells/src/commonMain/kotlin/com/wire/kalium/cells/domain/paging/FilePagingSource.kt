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
import com.wire.kalium.cells.domain.model.CellFile
import com.wire.kalium.cells.domain.usecase.GetCellFilesUseCase
import com.wire.kalium.common.functional.fold

internal class FilePagingSource(
    val query: String,
    val conversationId: String?,
    val pageSize: Int,
    val getCellFilesUseCase: GetCellFilesUseCase,
) : PagingSource<Int, CellFile>() {

    override suspend fun load(params: PagingSourceLoadParams<Int>): PagingSourceLoadResult<Int, CellFile> =
        getCellFilesUseCase(
            conversationId = conversationId,
            query = query,
            limit = pageSize,
            offset = params.key ?: 0,
        ).fold(
            {
                PagingSourceLoadResultError<Int, CellFile>(
                    throwable = Exception("Failed to load files")
                ) as PagingSourceLoadResult<Int, CellFile>
            },
            { files ->
                PagingSourceLoadResultPage(
                    data = files.data,
                    prevKey = null,
                    nextKey = files.pagination?.nextOffset
                ) as PagingSourceLoadResult<Int, CellFile>
            }
        )

    override fun getRefreshKey(state: PagingState<Int, CellFile>): Int? {
        return state.anchorPosition?.let { anchorPosition ->
            val anchorPage = state.closestPageToPosition(anchorPosition)
            anchorPage?.prevKey?.plus(1) ?: anchorPage?.nextKey?.minus(1)
        }
    }
}
