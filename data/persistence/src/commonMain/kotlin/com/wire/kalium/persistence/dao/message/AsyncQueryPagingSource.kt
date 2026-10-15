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
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.async.coroutines.awaitAsList
import app.cash.sqldelight.async.coroutines.awaitAsOne
import kotlin.coroutines.CoroutineContext
import kotlin.properties.Delegates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

/**
 * Loads a page of query rows and optionally enriches them in one database transaction.
 * The postprocessor must preserve row count and order so offsets remain valid.
 */
internal class AsyncQueryPagingSource<RowType : Any, ItemType : Any>(
    private val countQuery: Query<Long>,
    private val context: CoroutineContext,
    private val queryProvider: (limit: Long, offset: Long) -> Query<RowType>,
    private val initialOffset: Long = 0,
    private val postProcessor: suspend (List<RowType>) -> List<ItemType>,
    private val transacter: SuspendingTransacter? = null,
) : PagingSource<Int, ItemType>(), Query.Listener {
    companion object {
        /** With no postprocessor, the query rows are also the output items. */
        operator fun <RowType : Any> invoke(
            countQuery: Query<Long>,
            context: CoroutineContext,
            queryProvider: (limit: Long, offset: Long) -> Query<RowType>,
            initialOffset: Long = 0,
            transacter: SuspendingTransacter? = null,
        ): AsyncQueryPagingSource<RowType, RowType> = AsyncQueryPagingSource(
            countQuery = countQuery,
            context = context,
            queryProvider = queryProvider,
            initialOffset = initialOffset,
            postProcessor = { it },
            transacter = transacter,
        )
    }

    private var currentQuery: Query<RowType>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }

    override val jumpingSupported: Boolean get() = true

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    override fun queryResultsChanged() = invalidate()

    @Suppress("CyclomaticComplexMethod", "TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, ItemType> =
        withContext(context) {
            val loadPage: suspend (LoadParams<Int>) -> LoadResult<Int, ItemType> = { params ->
                val key = params.key?.toLong() ?: initialOffset
                val limit = when (params) {
                    is LoadParams.Prepend<*> -> minOf(key, params.loadSize.toLong())
                    else -> params.loadSize.toLong()
                }
                val count = countQuery.awaitAsOne().toInt()
                val offset = when (params) {
                    is LoadParams.Prepend<*> -> maxOf(0, key - params.loadSize).toInt()
                    is LoadParams.Append<*> -> key.toInt()
                    is LoadParams.Refresh<*> ->
                        if (key >= count - params.loadSize) maxOf(0, count - params.loadSize) else key.toInt()
                    else -> error("Unknown PagingSourceLoadParams ${params::class}")
                }
                val data = queryProvider(limit, offset.toLong())
                    .also { currentQuery = it }
                    .awaitAsList()
                    .let { rows -> postProcessor.invoke(rows) }
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
            }

            withContext(context) {
                try {
                    transacter?.transactionWithResult { loadPage(params) } ?: loadPage(params)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (exception: Exception) {
                    LoadResult.Error(exception)
                }
            }
        }

    override fun getRefreshKey(state: PagingState<Int, ItemType>): Int? =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
}
