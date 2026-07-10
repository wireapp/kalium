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
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

internal data class MessageCursor(
    val segment: Segment,
    val date: Instant,
    val id: String,
) {
    internal enum class Segment {
        PENDING,
        NON_PENDING,
    }
}

internal data class MessageSegmentQueries<RowType : Any>(
    val first: (limit: Long) -> Query<RowType>,
    val from: (cursor: MessageCursor, limit: Long) -> Query<RowType>,
    val after: (cursor: MessageCursor, limit: Long) -> Query<RowType>,
    val before: (cursor: MessageCursor, limit: Long) -> Query<RowType>,
)

internal data class InitialMessageCursor(
    val query: (() -> Query<MessageCursor>)? = null,
    val itemsBefore: Int = 0,
) {
    init {
        require(itemsBefore >= 0) { "itemsBefore cannot be negative" }
    }
}

internal data class MessageKeysetQueries<RowType : Any>(
    val pending: MessageSegmentQueries<RowType>,
    val lastPending: (limit: Long) -> Query<RowType>,
    val nonPending: MessageSegmentQueries<RowType>,
)

/**
 * Pages the message timeline using the stable `(pending segment, date, id)` ordering.
 *
 * Pending messages form the leading segment because the chat UI renders the list in reverse.
 * Queries in the forward direction return data order; queries before a cursor return reverse
 * data order so the closest rows can be selected without scanning the full prefix.
 */
internal class KeysetPendingMessagesPagingSource<RowType : Any>(
    private val context: CoroutineContext,
    private val queries: MessageKeysetQueries<RowType>,
    private val initialCursor: InitialMessageCursor = InitialMessageCursor(),
    private val cursorProvider: (RowType) -> MessageCursor,
    private val postProcessor: (suspend (List<RowType>) -> List<RowType>)? = null,
) : PagingSource<MessageCursor, RowType>(), Query.Listener {
    private val currentQueries = mutableListOf<Query<*>>()

    init {
        registerInvalidatedCallback(::clearQueryListeners)
    }

    override fun queryResultsChanged() = invalidate()

    @Suppress("TooGenericExceptionCaught")
    override suspend fun load(params: LoadParams<MessageCursor>): LoadResult<MessageCursor, RowType> =
        withContext(context) {
            try {
                clearQueryListeners()
                createPage(params, loadRows(params))
            } catch (exception: Exception) {
                LoadResult.Error(exception)
            }
        }

    override fun getRefreshKey(state: PagingState<MessageCursor, RowType>): MessageCursor? {
        val anchorPosition = state.anchorPosition ?: return null
        val refreshPosition = maxOf(0, anchorPosition - state.config.initialLoadSize / 2)
        return state.closestItemToPosition(refreshPosition)?.let(cursorProvider)
    }

    private suspend fun loadRows(params: LoadParams<MessageCursor>): List<RowType> = when (params) {
        is LoadParams.Refresh -> loadFrom(params.key ?: initialCursor(), params.loadSize + 1)
        is LoadParams.Append -> loadAfter(params.key, params.loadSize + 1)
        is LoadParams.Prepend -> loadBefore(params.key, params.loadSize + 1)
    }

    private suspend fun initialCursor(): MessageCursor? = initialCursor.query
        ?.invoke()
        ?.also { query ->
            query.addListener(this)
            currentQueries += query
        }
        ?.awaitAsOneOrNull()
        ?.shiftByConfiguredItemsBefore()

    private suspend fun MessageCursor.shiftByConfiguredItemsBefore(): MessageCursor {
        val rowsBefore = if (initialCursor.itemsBefore == 0) {
            emptyList()
        } else {
            loadBefore(this, initialCursor.itemsBefore)
        }
        return rowsBefore.firstOrNull()?.let(cursorProvider) ?: this
    }

    private suspend fun createPage(
        params: LoadParams<MessageCursor>,
        loaded: List<RowType>,
    ): LoadResult<MessageCursor, RowType> {
        val hasExtra = loaded.size > params.loadSize
        val rows = when (params) {
            is LoadParams.Prepend -> if (hasExtra) loaded.drop(1) else loaded
            else -> loaded.take(params.loadSize)
        }
        val pageData = postProcessor?.invoke(rows) ?: rows
        if (invalid) return LoadResult.Invalid()

        val firstCursor = pageData.firstOrNull()?.let(cursorProvider)
        val lastCursor = pageData.lastOrNull()?.let(cursorProvider)
        return LoadResult.Page(
            data = pageData,
            prevKey = when (params) {
                is LoadParams.Refresh -> firstCursor.takeIf { params.key != null || initialCursor.query != null }
                is LoadParams.Append -> firstCursor
                is LoadParams.Prepend -> firstCursor.takeIf { hasExtra }
            },
            nextKey = when (params) {
                is LoadParams.Prepend -> lastCursor
                else -> lastCursor.takeIf { hasExtra }
            },
            itemsBefore = LoadResult.Page.COUNT_UNDEFINED,
            itemsAfter = LoadResult.Page.COUNT_UNDEFINED,
        )
    }

    private suspend fun loadFrom(cursor: MessageCursor?, limit: Int): List<RowType> = when (cursor?.segment) {
        null -> loadPendingThenNonPending(queries.pending.first(limit.toLong()), limit)
        MessageCursor.Segment.PENDING -> loadPendingThenNonPending(queries.pending.from(cursor, limit.toLong()), limit)
        MessageCursor.Segment.NON_PENDING -> execute(queries.nonPending.from(cursor, limit.toLong()))
    }

    private suspend fun loadAfter(cursor: MessageCursor, limit: Int): List<RowType> = when (cursor.segment) {
        MessageCursor.Segment.PENDING -> loadPendingThenNonPending(queries.pending.after(cursor, limit.toLong()), limit)
        MessageCursor.Segment.NON_PENDING -> execute(queries.nonPending.after(cursor, limit.toLong()))
    }

    private suspend fun loadBefore(cursor: MessageCursor, limit: Int): List<RowType> {
        val rowsInReverseOrder = when (cursor.segment) {
            MessageCursor.Segment.PENDING -> execute(queries.pending.before(cursor, limit.toLong()))
            MessageCursor.Segment.NON_PENDING -> {
                val nonPending = execute(queries.nonPending.before(cursor, limit.toLong()))
                if (nonPending.size >= limit) {
                    nonPending
                } else {
                    nonPending + execute(queries.lastPending((limit - nonPending.size).toLong()))
                }
            }
        }
        return rowsInReverseOrder.reversed()
    }

    private suspend fun loadPendingThenNonPending(pendingQuery: Query<RowType>, limit: Int): List<RowType> {
        val pending = execute(pendingQuery)
        return if (pending.size >= limit) {
            pending
        } else {
            pending + execute(queries.nonPending.first((limit - pending.size).toLong()))
        }
    }

    private suspend fun execute(query: Query<RowType>): List<RowType> {
        query.addListener(this)
        currentQueries += query
        return query.awaitAsList()
    }

    private fun clearQueryListeners() {
        currentQueries.forEach { it.removeListener(this) }
        currentQueries.clear()
    }
}
