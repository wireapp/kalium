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

import app.cash.paging.Pager
import app.cash.paging.PagingConfig
import app.cash.sqldelight.paging3.QueryPagingSource
import com.wire.kalium.persistence.ConversationDetailsWithEventsQueries
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.message.KaliumPager
import kotlin.coroutines.CoroutineContext

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
        val newActivitiesOnTop: Boolean = false,
    )
}

internal class ConversationExtensionsImpl internal constructor(
    private val queries: ConversationDetailsWithEventsQueries,
    private val mapper: ConversationDetailsWithEventsMapper,
    private val coroutineContext: CoroutineContext,
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
            coroutineContext = coroutineContext,
        )

    private fun pagingSource(queryConfig: QueryConfig, initialOffset: Long) = with(queryConfig) {
        QueryPagingSource(
            countQuery =
            if (searchQuery.isBlank()) queries.countConversationDetailsWithEvents(fromArchive, onlyInteractionEnabled)
            else queries.countConversationDetailsWithEventsFromSearch(fromArchive, onlyInteractionEnabled, searchQuery),
            transacter = queries,
            context = coroutineContext,
            initialOffset = initialOffset,
            queryProvider = { limit, offset ->
                if (searchQuery.isBlank()) {
                    queries.selectConversationDetailsWithEvents(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        newActivitiesOnTop = newActivitiesOnTop,
                        limit = limit,
                        offset = offset,
                        mapper = mapper::fromViewToModel,
                    )
                } else {
                    queries.selectConversationDetailsWithEventsFromSearch(
                        fromArchive = fromArchive,
                        onlyInteractionsEnabled = onlyInteractionEnabled,
                        searchQuery = searchQuery,
                        newActivitiesOnTop = newActivitiesOnTop,
                        limit = limit,
                        offset = offset,
                        mapper = mapper::fromViewToModel,
                    )
                }
            }
        )
    }
}
