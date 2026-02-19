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

import androidx.paging.PagingData
import com.wire.kalium.cells.data.MIMEType
import com.wire.kalium.cells.data.Sorting
import com.wire.kalium.cells.domain.model.Node
import kotlinx.coroutines.flow.Flow

public interface GetPaginatedFilesFlowUseCase {
    /**
     * Use case to get a paginated flow of file [Node]s from cells.
     * @param conversationId The unique identifier of the conversation to filter files by (optional).
     * @param query The search query to filter files.
     * @param onlyDeleted Flag to indicate whether to fetch only deleted files.
     * @param tags List of tags to filter files.
     * @param owners List of owner IDs to filter files.
     * @param mimeTypes List of MIME types to filter files.
     * @param sorting The sorting method to apply to the results.
     * @param sortDescending Flag to indicate whether to sort in descending order.
     * @return a flow of paginated file nodes.
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

internal class GetPaginatedFilesFlowUseCaseImpl(
    private val getCellFilesUseCase: GetCellFilesPagedUseCase,
) : GetPaginatedFilesFlowUseCase {

    override suspend operator fun invoke(
        conversationId: String?,
        query: String,
        onlyDeleted: Boolean,
        tags: List<String>,
        owners: List<String>,
        mimeTypes: List<MIMEType>,
        sorting: Sorting,
        sortDescending: Boolean,
    ): Flow<PagingData<Node>> {
        return getCellFilesUseCase(conversationId, query, onlyDeleted, tags, owners, mimeTypes, sorting, sortDescending)
    }
}
