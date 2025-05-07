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
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.paging.FilePagingSource
import kotlinx.coroutines.flow.Flow

public interface GetCellFilesPagedUseCase {
    public suspend operator fun invoke(
        conversationId: String?,
        query: String,
    ): Flow<PagingData<Node.File>>
}

internal class GetCellFilesPagedUseCaseImpl(
    private val getCellFilesUseCase: GetCellFilesUseCase,
) : GetCellFilesPagedUseCase {

    private companion object {
        const val PAGE_SIZE = 30
    }

    override suspend operator fun invoke(
        conversationId: String?,
        query: String,
    ): Flow<PagingData<Node.File>> {
        return Pager(
            config = PagingConfig(
                pageSize = PAGE_SIZE
            ),
            pagingSourceFactory = {
                FilePagingSource(
                    query = query,
                    pageSize = PAGE_SIZE,
                    conversationId = conversationId,
                    getCellFilesUseCase = getCellFilesUseCase
                )
            }
        ).flow
    }
}
