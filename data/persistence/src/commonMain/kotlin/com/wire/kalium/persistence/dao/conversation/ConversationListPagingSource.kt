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
package com.wire.kalium.persistence.dao.conversation

import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.async.coroutines.awaitAsList
import com.wire.kalium.persistence.ConversationListQueries
import com.wire.kalium.persistence.SelectConversationListOlder
import com.wire.kalium.persistence.db.ReadDispatcher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext

internal class ConversationListPagingSource(
    private val queries: ConversationListQueries,
    private val query: ConversationListQueryEntity,
    private val readDispatcher: ReadDispatcher,
) : PagingSource<ConversationListPositionEntity, ConversationListItemEntity>(), Query.Listener {
    // All load directions use the same notification keys. Subscribe before any reads,
    // including the multiple queries used by a centered refresh.
    private val invalidationQuery = olderQuery(null, 1, false)

    init {
        invalidationQuery.addListener(this)
        registerInvalidatedCallback { invalidationQuery.removeListener(this) }
    }

    override fun queryResultsChanged() = invalidate()

    override fun getRefreshKey(
        state: PagingState<ConversationListPositionEntity, ConversationListItemEntity>,
    ): ConversationListPositionEntity? = state.anchorPosition?.let { state.closestItemToPosition(it)?.position }

    @Suppress("TooGenericExceptionCaught") // Paging reports platform database failures through LoadResult.Error.
    override suspend fun load(
        params: LoadParams<ConversationListPositionEntity>,
    ): LoadResult<ConversationListPositionEntity, ConversationListItemEntity> = withContext(readDispatcher.value) {
        try {
            if (invalid) return@withContext LoadResult.Invalid()
            val page = queries.transactionWithResult {
                when (params) {
                    is LoadParams.Refresh -> refresh(params.key, params.loadSize)
                    is LoadParams.Append -> {
                        val rows = older(params.key, params.loadSize + 1)
                        page(rows.take(params.loadSize), hasNewer = true, hasOlder = rows.size > params.loadSize)
                    }
                    is LoadParams.Prepend -> {
                        val rows = newer(params.key, params.loadSize + 1)
                        page(rows.take(params.loadSize).reversed(), hasNewer = rows.size > params.loadSize, hasOlder = true)
                    }
                }
            }
            if (invalid) LoadResult.Invalid() else page
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            LoadResult.Error(exception)
        }
    }

    private suspend fun refresh(
        position: ConversationListPositionEntity?,
        size: Int,
    ): LoadResult.Page<ConversationListPositionEntity, ConversationListItemEntity> {
        if (position == null) {
            val rows = older(null, size + 1)
            return page(rows.take(size), hasNewer = false, hasOlder = rows.size > size)
        }
        var newerLimit = size / 2
        var newerRows = newer(position, newerLimit + 1)
        val olderLimit = size - minOf(newerRows.size, newerLimit)
        val olderRows = older(position, olderLimit + 1, inclusive = true)
        // Near the oldest edge, fill the remainder from the newer side.
        if (olderRows.size < olderLimit) {
            newerLimit = size - olderRows.size
            newerRows = newer(position, newerLimit + 1)
        }
        return page(
            newerRows.take(newerLimit).reversed() + olderRows.take(olderLimit),
            hasNewer = newerRows.size > newerLimit,
            hasOlder = olderRows.size > olderLimit,
        )
    }

    private fun page(rows: List<SelectConversationListOlder>, hasNewer: Boolean, hasOlder: Boolean) = LoadResult.Page(
        data = rows.map { it.toListItem() },
        prevKey = rows.firstOrNull()?.position()?.takeIf { hasNewer },
        nextKey = rows.lastOrNull()?.position()?.takeIf { hasOlder },
    )

    private fun SelectConversationListOlder.position() = ConversationListPositionEntity(activityTime, conversationId)

    private suspend fun older(position: ConversationListPositionEntity?, size: Int, inclusive: Boolean = false) =
        olderQuery(position, size, inclusive).awaitAsList()

    private fun olderQuery(position: ConversationListPositionEntity?, size: Int, inclusive: Boolean) =
        queries.selectConversationListOlder(
            fromArchive = query.fromArchive,
            source = query.source.name,
            strictMls = if (query.strictMlsFilter) 1L else 0L,
            onlyInteractionEnabled = if (query.onlyInteractionEnabled) 1L else 0L,
            unreadOnly = if (query.unreadOnly) 1L else 0L,
            searchQuery = query.searchQuery.takeUnless { it.isBlank() }.orEmpty(),
            activityTime = position?.activityTime?.toEpochMilliseconds(),
            conversationId = position?.conversationId,
            inclusive = if (inclusive) 1L else 0L,
            pageSize = size.toLong(),
        )

    private suspend fun newer(position: ConversationListPositionEntity, size: Int) =
        queries.selectConversationListNewer(
            fromArchive = query.fromArchive,
            source = query.source.name,
            strictMls = if (query.strictMlsFilter) 1L else 0L,
            onlyInteractionEnabled = if (query.onlyInteractionEnabled) 1L else 0L,
            unreadOnly = if (query.unreadOnly) 1L else 0L,
            searchQuery = query.searchQuery.takeUnless { it.isBlank() }.orEmpty(),
            activityTime = position.activityTime,
            conversationId = position.conversationId,
            pageSize = size.toLong(),
            mapper = ::SelectConversationListOlder,
        ).awaitAsList()
}
