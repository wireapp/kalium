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
package com.wire.kalium.logic.data.conversation

import app.cash.paging.PagingConfig
import app.cash.paging.PagingData
import app.cash.paging.map
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.message.KaliumPager
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Mockable
interface ConversationRepositoryExtensions {
    suspend fun getPaginatedConversationDetailsWithEventsBySearchQuery(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
    ): Flow<PagingData<ConversationDetailsWithEvents>>
}

class ConversationRepositoryExtensionsImpl internal constructor(
    private val conversationDAO: ConversationDAO,
    private val conversationMapper: ConversationMapper
) : ConversationRepositoryExtensions {
    override suspend fun getPaginatedConversationDetailsWithEventsBySearchQuery(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long
    ): Flow<PagingData<ConversationDetailsWithEvents>> {
        val pager: KaliumPager<ConversationDetailsWithEventsEntity> = with(queryConfig) {
            conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
                queryConfig = QueryConfig(
                    searchQuery = searchQuery,
                    fromArchive = fromArchive,
                    onlyInteractionEnabled = onlyInteractionEnabled,
                    newActivitiesOnTop = newActivitiesOnTop,
                    conversationFilter = conversationFilter.toDao()
                ),
                pagingConfig = pagingConfig
            )
        }

        return pager.pagingDataFlow.map { pagingData ->
            pagingData
                .map { conversationDetailsWithEventsEntity ->
                    conversationMapper.fromDaoModelToDetailsWithEvents(
                        conversationDetailsWithEventsEntity
                    )
                }
        }
    }
}

data class ConversationQueryConfig(
    val searchQuery: String = "",
    val fromArchive: Boolean = false,
    val onlyInteractionEnabled: Boolean = false,
    val newActivitiesOnTop: Boolean = false,
    val conversationFilter: ConversationFilter = ConversationFilter.All,
)
