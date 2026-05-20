/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import app.cash.sqldelight.Query
import app.cash.sqldelight.SuspendingTransacter
import app.cash.sqldelight.Transacter
import app.cash.sqldelight.TransacterBase
import app.cash.sqldelight.TransactionCallbacks
import com.wire.kalium.persistence.ConversationDetailsWithEventsQueries
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.kaliumLogger
import kotlinx.coroutines.withContext
import kotlin.properties.Delegates
import kotlin.time.TimeSource

interface ConversationExtensions {
    fun getPagerForConversationDetailsWithEventsSearch(
        queryConfig: QueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long = 0,
    ): KaliumPager<ConversationDetailsWithEventsEntity>

    data class QueryConfig(
        val searchQuery: String = "",
        val fromArchive: Boolean = false,
        val onlyInteractionEnabled: Boolean = false,
        val conversationFilter: ConversationFilterEntity = ConversationFilterEntity.ALL,
        val strictMlsFilter: Boolean = true,
    )
}

internal class ConversationExtensionsImpl internal constructor(
    private val queries: ConversationDetailsWithEventsQueries,
    private val mapper: ConversationDetailsWithEventsMapper,
    private val readDispatcher: ReadDispatcher,
) : ConversationExtensions {
    override fun getPagerForConversationDetailsWithEventsSearch(
        queryConfig: QueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): KaliumPager<ConversationDetailsWithEventsEntity> =
        KaliumPager(
            // We could return a Flow directly, but having the PagingSource is the only way to test this
            pager = Pager(pagingConfig) {
                pagingSource(queryConfig, startingOffset)
            },
            pagingSource = pagingSource(queryConfig, startingOffset),
            readDispatcher = readDispatcher,
        )

    /**
     * Uses lightweight COUNT when `searchQuery` is empty.
     *
     * Architectural decision explained in ADR-003:
     * https://github.com/wireapp/kalium/tree/develop/docs/adr/0003-lightweight-count-for-faster-conversation-list-loading.md
     *
     * Summary:
     * - COUNT on Conversation is significantly faster than COUNT on ConversationDetails
     * - Only used when search is empty
     * - SELECT still uses full ConversationDetails rules (COUNT can be a superset)
     */
    private fun pagingSource(queryConfig: QueryConfig, initialOffset: Long) = with(queryConfig) {
        TimedConversationDetailsWithEventsPagingSource(
            countQuery =
                if (searchQuery.isBlank()) {
                    queries.countConversations(
                        fromArchive = fromArchive,
                        conversationFilter = conversationFilter.name,
                        strict_mls = if (strictMlsFilter) 1 else 0,
                    )
                } else {
                    queries.countConversationDetailsWithEventsFromSearch(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        searchQuery = searchQuery,
                        strict_mls = if (strictMlsFilter) 1 else 0,
                    )
                },
            transacter = queries,
            context = readDispatcher.value,
            initialOffset = initialOffset,
            queryConfig = queryConfig,
            queryProvider = { limit, offset ->
                if (searchQuery.isBlank()) {
                    queries.selectConversationDetailsWithEvents(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        limit = limit,
                        offset = offset,
                        strict_mls = if (queryConfig.strictMlsFilter) 1 else 0,
                        mapper = mapper::fromPagedViewToModel,
                    )
                } else {
                    queries.selectConversationDetailsWithEventsFromSearch(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        conversationFilter = conversationFilter.name,
                        searchQuery = searchQuery,
                        limit = limit,
                        offset = offset,
                        strict_mls = if (queryConfig.strictMlsFilter) 1 else 0,
                        mapper = mapper::fromPagedViewToModel,
                    )
                }
            }
        )
    }
}

private class TimedConversationDetailsWithEventsPagingSource(
    private val queryProvider: (limit: Long, offset: Long) -> Query<ConversationDetailsWithEventsEntity>,
    private val countQuery: Query<Long>,
    private val transacter: TransacterBase,
    private val context: kotlin.coroutines.CoroutineContext,
    private val initialOffset: Long,
    private val queryConfig: QueryConfig,
) : PagingSource<Int, ConversationDetailsWithEventsEntity>(), Query.Listener {

    private var currentQuery: Query<ConversationDetailsWithEventsEntity>? by Delegates.observable(null) { _, old, new ->
        old?.removeListener(this)
        new?.addListener(this)
    }

    override val jumpingSupported: Boolean
        get() = true

    init {
        registerInvalidatedCallback {
            currentQuery?.removeListener(this)
            currentQuery = null
        }
    }

    override fun queryResultsChanged() = invalidate()

    override suspend fun load(
        params: LoadParams<Int>,
    ): LoadResult<Int, ConversationDetailsWithEventsEntity> = withContext(context) {
        val key = (params.key ?: initialOffset.toInt()).toLong()
        val limit = when (params) {
            is LoadParams.Prepend -> minOf(key, params.loadSize.toLong())
            else -> params.loadSize.toLong()
        }
        val loadType = when (params) {
            is LoadParams.Append -> "append"
            is LoadParams.Prepend -> "prepend"
            is LoadParams.Refresh -> "refresh"
        }

        var countDurationMs = 0L
        var queryDurationMs = 0L
        var count = 0L
        var offset = 0L
        val totalStart = TimeSource.Monotonic.markNow()

        val loadResult = runCatching {
            val getPagingSourceLoadResult: TransactionCallbacks.() -> LoadResult.Page<Int, ConversationDetailsWithEventsEntity> = {
                val countStart = TimeSource.Monotonic.markNow()
                count = countQuery.executeAsOne()
                countDurationMs = countStart.elapsedNow().inWholeMilliseconds

                offset = when (params) {
                    is LoadParams.Prepend -> maxOf(0L, key - params.loadSize)
                    is LoadParams.Append -> key
                    is LoadParams.Refresh -> if (key >= count - params.loadSize) maxOf(0L, count - params.loadSize) else key
                }

                val queryStart = TimeSource.Monotonic.markNow()
                val data = queryProvider(limit, offset)
                    .also { currentQuery = it }
                    .executeAsList()
                queryDurationMs = queryStart.elapsedNow().inWholeMilliseconds

                val nextPosToLoad = offset + data.size
                LoadResult.Page(
                    data = data,
                    prevKey = offset.toInt().takeIf { it > 0 && data.isNotEmpty() },
                    nextKey = nextPosToLoad.toInt().takeIf { data.isNotEmpty() && data.size >= limit && nextPosToLoad < count },
                    itemsBefore = offset.toInt(),
                    itemsAfter = maxOf(0L, count - nextPosToLoad).toInt(),
                )
            }

            when (transacter) {
                is Transacter -> transacter.transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
                is SuspendingTransacter -> transacter.transactionWithResult(bodyWithReturn = getPagingSourceLoadResult)
            }
        }

        val totalDurationMs = totalStart.elapsedNow().inWholeMilliseconds
        loadResult
            .onSuccess { page ->
                kaliumLogger.i(
                    "[ConversationDetailsWithEventsPaging] " +
                        "loadType=$loadType " +
                        "limit=$limit " +
                        "offset=$offset " +
                        "rows=${page.data.size} " +
                        "count=$count " +
                        "countMs=$countDurationMs " +
                        "queryMs=$queryDurationMs " +
                        "totalMs=$totalDurationMs " +
                        "search=${queryConfig.searchQuery.isNotBlank()} " +
                        "filter=${queryConfig.conversationFilter.name} " +
                        "archive=${queryConfig.fromArchive} " +
                        "onlyInteraction=${queryConfig.onlyInteractionEnabled}"
                )
            }
            .onFailure { error ->
                kaliumLogger.e(
                    "[ConversationDetailsWithEventsPaging] " +
                        "failed loadType=$loadType limit=$limit offset=$offset totalMs=$totalDurationMs",
                    error
                )
            }

        if (invalid) LoadResult.Invalid() else loadResult.getOrThrow()
    }

    override fun getRefreshKey(state: PagingState<Int, ConversationDetailsWithEventsEntity>): Int? =
        state.anchorPosition?.let { maxOf(0, it - (state.config.initialLoadSize / 2)) }
}
