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
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationDetailsWithEvents
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationQueryConfig
import com.wire.kalium.util.KaliumDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return a flow of paginated searched conversation details with last message and unread events counts.
 * @see PagingData
 * @see ConversationDetailsWithEvents
 */
// todo(interface). extract interface for use case
public class GetPaginatedFlowOfConversationDetailsWithEventsBySearchQueryUseCase internal constructor(
    private val dispatcher: KaliumDispatcher,
    private val conversationRepository: ConversationRepository,
    private val callRepository: CallRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    public suspend operator fun invoke(
        queryConfig: ConversationQueryConfig,
        pagingConfig: PagingConfig,
        startingOffset: Long,
        strictMlsFilter: Boolean
    ): Flow<PagingData<ConversationDetailsWithEvents>> = callRepository.joinableCallsFlow()
        .map { joinableCalls -> joinableCalls.map { it.conversationId }.toSet() }
        .distinctUntilChanged()
        .flatMapLatest { joinableCallConversationIds ->
            conversationRepository.extensions
                .getPaginatedConversationDetailsWithEventsBySearchQuery(
                    queryConfig = queryConfig,
                    pagingConfig = pagingConfig,
                    startingOffset = startingOffset,
                    strictMlsFilter = strictMlsFilter,
                    ongoingCallConversationIds = joinableCallConversationIds.toList()
                )
        }
        .flowOn(dispatcher.io)
}
