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
package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.conversation.ConversationListItem
import com.wire.kalium.logic.data.conversation.ConversationListPosition
import com.wire.kalium.logic.data.conversation.ConversationListQuery
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.Flow

/**
 * Observes conversations in activity-time order, without counts or placeholders.
 * Favorites and folders remain on the existing list API. Archived rows have no preview.
 * Draft changes become visible on the next refresh; clients can refresh when returning to the list.
 */
public interface ObserveConversationListItemsUseCase {
    public operator fun invoke(
        pagingConfig: PagingConfig,
        query: ConversationListQuery = ConversationListQuery(),
        initialPosition: ConversationListPosition? = null,
    ): Flow<PagingData<ConversationListItem>>
}

internal class ObserveConversationListItemsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
) : ObserveConversationListItemsUseCase {
    override fun invoke(
        pagingConfig: PagingConfig,
        query: ConversationListQuery,
        initialPosition: ConversationListPosition?,
    ): Flow<PagingData<ConversationListItem>> {
        require(!pagingConfig.enablePlaceholders) { "Conversation list V2 does not support placeholders" }
        require(pagingConfig.jumpThreshold == androidx.paging.PagingSource.LoadResult.Page.COUNT_UNDEFINED) {
            "Conversation list V2 does not support jumping"
        }
        return conversationRepository.extensions.observeConversationListItems(pagingConfig, query, initialPosition)
    }
}
