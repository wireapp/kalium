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
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.dao.message.KaliumPager
import kotlin.coroutines.CoroutineContext

interface ConversationExtensions {
    fun getPagerForConversationDetailsWithEventsSearch(
        pagingConfig: PagingConfig,
        searchQuery: String = "",
        fromArchive: Boolean = false,
        onlyInteractionEnabled: Boolean = false,
        newActivitiesOnTop: Boolean = false,
        startingOffset: Long = 0,
    ): KaliumPager<ConversationDetailsWithEventsEntity>
}

internal class ConversationExtensionsImpl internal constructor(
    private val queries: ConversationsQueries,
    private val mapper: ConversationDetailsWithEventsMapper,
    private val coroutineContext: CoroutineContext,
) : ConversationExtensions {
    override fun getPagerForConversationDetailsWithEventsSearch(
        pagingConfig: PagingConfig,
        searchQuery: String,
        fromArchive: Boolean,
        onlyInteractionEnabled: Boolean,
        newActivitiesOnTop: Boolean,
        startingOffset: Long
    ): KaliumPager<ConversationDetailsWithEventsEntity> =
        KaliumPager( // We could return a Flow directly, but having the PagingSource is the only way to test this
            Pager(pagingConfig) { pagingSource(searchQuery, fromArchive, onlyInteractionEnabled, newActivitiesOnTop, startingOffset) },
            pagingSource(searchQuery, fromArchive, onlyInteractionEnabled, newActivitiesOnTop, startingOffset),
            coroutineContext
        )

    private fun pagingSource(
        searchQuery: String,
        fromArchive: Boolean,
        onlyInteractionEnabled: Boolean,
        newActivitiesOnTop: Boolean,
        initialOffset: Long
    ) = QueryPagingSource(
        countQuery = queries.countConversationDetailsWithEventsFromSearch(fromArchive, onlyInteractionEnabled, searchQuery),
        transacter = queries,
        context = coroutineContext,
        initialOffset = initialOffset,
        queryProvider = { limit, offset ->
            queries.selectConversationDetailsWithEventsFromSearch(
                fromArchive, onlyInteractionEnabled, searchQuery, newActivitiesOnTop, limit, offset, mapper::fromViewToModel
            )
        }
    )
}
