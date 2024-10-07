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
package com.wire.kalium.logic.feature.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn

/**
 * This use case will observe and return a flow of paginated searched conversation details with last message and unread events counts.
 * @see PagingData
 * @see ConversationDetailsWithEvents
 */
class GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val conversationRepository: ConversationRepository,
) {
    suspend operator fun invoke(
        searchQuery: String,
        fromArchive: Boolean,
        startingOffset: Long,
        pagingConfig: PagingConfig
    ): Flow<PagingData<ConversationDetailsWithEvents>> = conversationRepository.extensions
        .getPaginatedConversationDetailsWithEventsBySearchQuery(searchQuery, fromArchive, pagingConfig, startingOffset)
        .flowOn(dispatcher.io)
}
