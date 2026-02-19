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
package com.wire.kalium.cells.domain.usecase

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import com.wire.kalium.cells.data.MIMEType
import com.wire.kalium.cells.data.Sorting
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.paging.FilePagingSource
import kotlinx.coroutines.flow.Flow

public interface GetCellFilesPagedUseCase {
    /**
     * Use case to get paged files from a cell, optionally filtered by conversation ID, search query,
     * deletion status, and tags.
     *
     * @param conversationId The ID of the conversation to filter files by (optional).
     * @param query The search query to filter files.
     * @param onlyDeleted Whether to include only deleted files.
     * @param tags A list of tags to filter files by.
     * @param owners A list of owner IDs to filter files by.
     * @param mimeTypes A list of MIME types to filter files by.
     * @param sorting The sorting method to apply to the results.
     * @param sortDescending Whether to sort in descending order or ascending order.
     * @return A flow of paged data containing the filtered files.
     */
    public suspend operator fun invoke(
        conversationId: String?,
        query: String,
        onlyDeleted: Boolean = false,
        tags: List<String> = emptyList(),
        owners: List<String> = emptyList(),
        mimeTypes: List<MIMEType> = emptyList(),
        sorting: Sorting = Sorting.FOLDERS_FIRST_THEN_ALPHABETICAL,
        sortDescending: Boolean = true,
    ): Flow<PagingData<Node>>
}

internal class GetCellFilesPagedUseCaseImpl(
    private val getPaginatedNodesUseCase: GetPaginatedNodesUseCase,
) : GetCellFilesPagedUseCase {

    private companion object {
        const val PAGE_SIZE = 30
    }

    override suspend operator fun invoke(
        conversationId: String?,
        query: String,
        onlyDeleted: Boolean,
        tags: List<String>,
        owners: List<String>,
        mimeTypes: List<MIMEType>,
        sorting: Sorting = Sorting.FOLDERS_FIRST_THEN_ALPHABETICAL,
        sortDescending: Boolean = true,
    ): Flow<PagingData<Node>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE
            ),
            pagingSourceFactory = {
                FilePagingSource(
                    query = query,
                    pageSize = PAGE_SIZE,
                    conversationId = conversationId,
                    getPaginatedNodesUseCase = getPaginatedNodesUseCase,
                    onlyDeleted = onlyDeleted,
                    tags = tags,
                    owners = owners,
                    mimeTypes = mimeTypes,
                    sorting = sorting,
                    sortDescending = sortDescending
                )
            }
        ).flow
    }
}
