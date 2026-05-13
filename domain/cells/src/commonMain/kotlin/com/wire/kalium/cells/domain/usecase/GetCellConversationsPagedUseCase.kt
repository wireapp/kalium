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
package com.wire.kalium.cells.domain.usecase

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.model.CellConversation
import com.wire.kalium.cells.domain.paging.ConversationPagingSource
import kotlinx.coroutines.flow.Flow

/**
 * Use case to get paginated cell conversations.
 */
public interface GetCellConversationsPagedUseCase {
    public operator fun invoke(query: String = ""): Flow<PagingData<CellConversation>>
}

internal class GetCellConversationsPagedUseCaseImpl(
    private val repository: CellConversationRepository,
) : GetCellConversationsPagedUseCase {
    override operator fun invoke(query: String): Flow<PagingData<CellConversation>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE),
            pagingSourceFactory = { ConversationPagingSource(PAGE_SIZE, repository, query) },
        ).flow

    private companion object {
        const val PAGE_SIZE = 8
    }
}
