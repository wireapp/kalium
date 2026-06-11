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

package com.wire.kalium.persistence.dao.message

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext

/**
 * Paging source for conversation messages where pending messages must be exposed as a dedicated
 * leading segment.
 *
 * This is used by the conversation message pager because the chat UI uses `reverseLayout = true`:
 * emitting pending messages first in data order renders them at the visual bottom of the chat,
 * close to the composer. A regular single-query paging source cannot keep efficient pending and
 * non-pending SQL queries while also translating Paging's global offsets across that boundary.
 */
@Suppress("LongParameterList")
internal class PendingMessagesPagingSource<RowType : Any>(
    private val totalCountQuery: Query<Long>,
    private val pendingCountQuery: Query<Long>,
    private val context: CoroutineContext,
    private val pendingQueryProvider: (limit: Long, offset: Long) -> Query<RowType>,
    private val nonPendingQueryProvider: (limit: Long, offset: Long) -> Query<RowType>,
    private val initialOffset: Long = 0,
    private val postProcessor: (suspend (List<RowType>) -> List<RowType>)? = null,
) : PagingSource<Int, RowType>(), Query.Listener {
    // The page is backed by two SQL result sets, but callers see one continuous list:
    // pending rows first, followed by non-pending rows. Track both active queries so a
    // change in either segment invalidates the paging source.
    private var currentQueries: List<Query<RowType>> = emptyList()
        set(value) {
            field.forEach { it.removeListener(this) }
            field = value
            field.forEach { it.addListener(this) }
        }

    override val jumpingSupported: Boolean get() = true

    init {
        registerInvalidatedCallback {
            currentQueries = emptyList()
        }
    }

    override fun queryResultsChanged() = invalidate()

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, RowType> =
        withContext(context) {
            try {
                val key = params.key?.toLong() ?: initialOffset
                val limit = when (params) {
                    is LoadParams.Prepend<*> -> minOf(key, params.loadSize.toLong())
                    else -> params.loadSize.toLong()
                }
                val pendingCount = pendingCountQuery.awaitAsOne().toInt()
                val count = totalCountQuery.awaitAsOne().toInt()

                // Paging still uses global offsets across the merged list. For example, with
                // 25 pending rows, offset 20 and limit 20 means "5 pending rows, then 15
                // non-pending rows", not "20 rows from each query".
                val offset = when (params) {
                    is LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize).toInt()
                    is LoadParams.Append<*> -> key.toInt()
                    is LoadParams.Refresh<*> ->
                        if (key >= count - params.loadSize) maxOf(0, count - params.loadSize) else key.toInt()
                    else -> error("Unknown PagingSourceLoadParams ${params::class}")
                }

                val queries = buildQueries(limit, offset, pendingCount)
                currentQueries = queries
                val data = queries
                    .flatMap { it.awaitAsList() }
                    .let { rows -> postProcessor?.invoke(rows) ?: rows }
                val nextPosition = offset + data.size

                if (invalid) {
                    LoadResult.Invalid()
                } else {
                    LoadResult.Page(
                        data = data,
                        prevKey = offset.takeIf { it > 0 && data.isNotEmpty() },
                        nextKey = nextPosition.takeIf { data.isNotEmpty() && data.size >= limit && it < count },
                        itemsBefore = offset,
                        itemsAfter = maxOf(0, count - nextPosition),
                    )
                }
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }

    override fun getRefreshKey(state: PagingState<Int, RowType>): Int? =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }

    private fun buildQueries(limit: Long, offset: Int, pendingCount: Int): List<Query<RowType>> {
        if (limit == 0L) return emptyList()

        return buildList {
            // First consume the requested slice from the pending segment when the global offset
            // still points inside it. The remaining limit, if any, continues in non-pending rows.
            val remainingLimit = if (offset < pendingCount) {
                val pendingLimit = minOf(limit, pendingCount.toLong() - offset)
                add(pendingQueryProvider(pendingLimit, offset.toLong()))
                limit - pendingLimit
            } else {
                limit
            }

            if (remainingLimit > 0L) {
                // Once the global offset has passed the pending segment, translate it into a
                // non-pending offset by subtracting the number of pending rows.
                val nonPendingOffset = maxOf(0, offset - pendingCount).toLong()
                add(nonPendingQueryProvider(remainingLimit, nonPendingOffset))
            }
        }
    }
}
