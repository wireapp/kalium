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

package com.wire.kalium.logic.data.message.paging

import app.cash.paging.PagingSource
import app.cash.paging.PagingSourceLoadParams
import app.cash.paging.PagingSourceLoadParamsAppend
import app.cash.paging.PagingSourceLoadResult
import app.cash.paging.PagingSourceLoadResultPage
import app.cash.paging.PagingState
import com.wire.kalium.persistence.dao.message.MessageEntity

internal class NomadMessagePagingSource(
    private val delegate: PagingSource<Int, MessageEntity>,
    private val onEmptyAppend: suspend (PagingSource<Int, MessageEntity>) -> Unit,
) : PagingSource<Int, MessageEntity>() {

    override suspend fun load(
        params: PagingSourceLoadParams<Int>
    ): PagingSourceLoadResult<Int, MessageEntity> {
        val result = delegate.load(params)
        if (params is PagingSourceLoadParamsAppend<*> &&
            result is PagingSourceLoadResultPage<*, *> &&
            result.data.isEmpty()
        ) {
            onEmptyAppend(this)
        }
        return result
    }

    override fun getRefreshKey(state: PagingState<Int, MessageEntity>): Int? =
        delegate.getRefreshKey(state)
}
