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

package com.wire.kalium.logic.feature.message

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.timeline.MessageListItem
import com.wire.kalium.logic.data.message.timeline.MessageTimeline
import com.wire.kalium.logic.data.message.timeline.MessageTimelinePagingSource
import com.wire.kalium.logic.data.message.timeline.MessageTimelinePolicy
import com.wire.kalium.logic.data.message.timeline.MessageTimelineRepository
import com.wire.kalium.logic.data.message.timeline.MessageTimelineRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/** Opens a message timeline generation. The caller observes invalidation and closes the session when leaving. */
public class OpenMessageTimelineUseCase internal constructor(
    private val repository: MessageTimelineRepository,
) {
    public operator fun invoke(
        conversationId: ConversationId,
        policy: MessageTimelinePolicy = MessageTimelinePolicy.DEFAULT,
    ): MessageTimeline = repository.open(conversationId, policy)
}

/** Paging uses message anchors, undefined positional counts and bounded page retention. */
public class GetPaginatedFlowOfMessageListItemsUseCase internal constructor(
    private val repository: MessageTimelineRepository,
) {
    public operator fun invoke(
        conversationId: ConversationId,
        policy: MessageTimelinePolicy = MessageTimelinePolicy.DEFAULT,
        initialRequest: MessageTimelineRequest = MessageTimelineRequest.Latest,
        pagingConfig: PagingConfig = PagingConfig(
            pageSize = 20,
            initialLoadSize = 60,
            prefetchDistance = 30,
            enablePlaceholders = false,
            maxSize = 200,
        ),
    ): Flow<PagingData<MessageListItem>> {
        require(!pagingConfig.enablePlaceholders) { "Message timelines use anchors instead of placeholders" }
        require(pagingConfig.jumpThreshold == Int.MIN_VALUE) { "Message timelines do not support positional jumps" }
        require(
            pagingConfig.pageSize <= repository.maxPageSize &&
                pagingConfig.initialLoadSize <= repository.maxPageSize
        ) { "Message timeline loads are limited to 100 items" }
        require(initialRequest !is MessageTimelineRequest.Older && initialRequest !is MessageTimelineRequest.Newer) {
            "Open at latest, a message anchor, or the read boundary; continuation cursors belong to an existing generation"
        }
        return flow {
            var source: MessageTimelinePagingSource? = null
            try {
                val pager = Pager(pagingConfig) {
                    source?.invalidate()
                    repository.pagingSource(conversationId, policy, initialRequest).also { source = it }
                }
                emitAll(pager.flow)
            } finally {
                source?.invalidate()
            }
        }
    }
}
