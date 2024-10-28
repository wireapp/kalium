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
import com.wire.kalium.logic.data.message.MessageMapper
import com.wire.kalium.logic.data.message.UnreadEventType
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig
import com.wire.kalium.persistence.dao.message.KaliumPager
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConversationRepositoryExtensions {
    suspend fun getPaginatedConversationDetailsWithEventsBySearchQuery(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
    ): Flow<PagingData<ConversationDetailsWithEvents>>
}

class ConversationRepositoryExtensionsImpl internal constructor(
    private val conversationDAO: ConversationDAO,
    private val conversationMapper: ConversationMapper,
    private val messageMapper: MessageMapper,
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

        return pager.pagingDataFlow.map {
            it.map {
                ConversationDetailsWithEvents(
                    conversationDetails = conversationMapper.fromDaoModelToDetails(it.conversationViewEntity),
                    lastMessage = when {
                        it.messageDraft != null -> messageMapper.fromDraftToMessagePreview(it.messageDraft!!)
                        it.lastMessage != null -> messageMapper.fromEntityToMessagePreview(it.lastMessage!!)
                        else -> null
                    },
                    unreadEventCount = it.unreadEvents.unreadEvents.mapKeys {
                        when (it.key) {
                            UnreadEventTypeEntity.KNOCK -> UnreadEventType.KNOCK
                            UnreadEventTypeEntity.MISSED_CALL -> UnreadEventType.MISSED_CALL
                            UnreadEventTypeEntity.MENTION -> UnreadEventType.MENTION
                            UnreadEventTypeEntity.REPLY -> UnreadEventType.REPLY
                            UnreadEventTypeEntity.MESSAGE -> UnreadEventType.MESSAGE
                        }
                    },
                    hasNewActivitiesToShow = it.hasNewActivitiesToShow,
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
    val conversationFilter: ConversationFilter = ConversationFilter.ALL,
)
