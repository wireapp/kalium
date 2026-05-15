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
package app.cash.sqldelight.paging3

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterBase
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import kotlin.math.max
import kotlin.math.min

internal abstract class QueryPagingSource<Key : Any, RowType : Any> :
    PagingSource<Key, RowType>(),
    Query.Listener {

    protected var currentQuery: Query<RowType>? = null
        set(value) {
            field?.removeListener(this)
            field = value
            field?.addListener(this)
        }

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    final override fun queryResultsChanged() = invalidate()
}

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun <RowType : Any> QueryPagingSource(
    countQuery: Query<Int>,
    transacter: TransacterBase,
    context: CoroutineContext,
    queryProvider: (limit: Int, offset: Int) -> Query<RowType>,
    initialOffset: Int = 0,
): PagingSource<Int, RowType> =
    OffsetQueryPagingSource(
        countProvider = { countQuery.executeAsOne() },
        context = context,
        queryProvider = queryProvider,
        initialOffset = initialOffset,
    )

@Suppress("FunctionName", "UNUSED_PARAMETER")
fun <RowType : Any> QueryPagingSource(
    countQuery: Query<Long>,
    transacter: TransacterBase,
    context: CoroutineContext,
    queryProvider: (limit: Long, offset: Long) -> Query<RowType>,
    initialOffset: Long = 0,
): PagingSource<Int, RowType> =
    OffsetQueryPagingSource(
        countProvider = { countQuery.executeAsOne().coerceToInt() },
        context = context,
        queryProvider = { limit, offset -> queryProvider(limit.toLong(), offset.toLong()) },
        initialOffset = initialOffset.coerceToInt(),
    )

private class OffsetQueryPagingSource<RowType : Any>(
    private val countProvider: () -> Int,
    private val context: CoroutineContext,
    private val queryProvider: (limit: Int, offset: Int) -> Query<RowType>,
    private val initialOffset: Int,
) : QueryPagingSource<Int, RowType>() {

    override fun getRefreshKey(state: PagingState<Int, RowType>): Int? =
        state.anchorPosition?.let { anchorPosition ->
            state.closestPageToPosition(anchorPosition)?.let { anchorPage ->
                anchorPage.prevKey?.plus(state.config.pageSize)
                    ?: anchorPage.nextKey?.minus(state.config.pageSize)
            }
        }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RowType> =
        withContext(context) {
            try {
                val totalCount = countProvider()
                val offset = when (params) {
                    is LoadParams.Refresh -> params.key ?: initialOffset
                    is LoadParams.Append -> params.key
                    is LoadParams.Prepend -> params.key
                }.coerceIn(0, totalCount)

                val data = if (offset >= totalCount) {
                    emptyList()
                } else {
                    queryProvider(params.loadSize, offset)
                        .also { currentQuery = it }
                        .executeAsList()
                }

                val nextOffset = offset + data.size
                LoadResult.Page(
                    data = data,
                    prevKey = if (offset == 0) null else max(offset - params.loadSize, 0),
                    nextKey = if (data.isEmpty() || nextOffset >= totalCount) null else nextOffset,
                    itemsBefore = offset,
                    itemsAfter = max(totalCount - nextOffset, 0),
                )
            } catch (throwable: Throwable) {
                LoadResult.Error(throwable)
            }
        }
}

private fun Int.coerceIn(minimumValue: Int, maximumValue: Int): Int =
    min(max(this, minimumValue), maximumValue)

private fun Long.coerceToInt(): Int =
    coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()
