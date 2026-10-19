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

package com.wire.kalium.logic.data.message.timeline

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageTimelineDAO

internal class MessageTimelinePagingSource(
    dao: MessageTimelineDAO,
    selfUserId: UserId,
    conversationId: ConversationId,
    policy: MessageTimelinePolicy,
    private val initialRequest: MessageTimelineRequest,
) : PagingSource<MessageTimelineCursor, MessageListItem>() {
    private val timeline = MessageTimeline(
        dao.open(conversationId.toDao(), policy == MessageTimelinePolicy.DEFAULT, ::invalidate),
        selfUserId
    )

    init {
        registerInvalidatedCallback(timeline::close)
    }

    override suspend fun load(params: LoadParams<MessageTimelineCursor>): LoadResult<MessageTimelineCursor, MessageListItem> {
        val request = when (params) {
            is LoadParams.Refresh -> params.key?.let { MessageTimelineRequest.AroundMessage(it.messageId, it) } ?: initialRequest
            is LoadParams.Append -> MessageTimelineRequest.Older(params.key)
            is LoadParams.Prepend -> MessageTimelineRequest.Newer(params.key)
        }
        return when (val result = timeline.load(request, params.loadSize)) {
            is MessageTimelineResult.Success -> if (invalid) {
                LoadResult.Invalid()
            } else {
                LoadResult.Page(result.page.items, result.page.newerCursor, result.page.olderCursor)
            }
            MessageTimelineResult.Invalidated -> LoadResult.Invalid()
            MessageTimelineResult.AnchorNotFound -> LoadResult.Error(IllegalArgumentException("The message anchor is no longer available"))
            MessageTimelineResult.InvalidCursor ->
                LoadResult.Error(IllegalArgumentException("The cursor belongs to another timeline generation"))
            MessageTimelineResult.InvalidLimit -> LoadResult.Error(IllegalArgumentException("The message limit must be positive"))
            is MessageTimelineResult.Failure -> LoadResult.Error(
                (result.error as? StorageFailure.Generic)?.rootCause ?: IllegalStateException("Message timeline load failed")
            )
        }
    }

    override fun getRefreshKey(state: PagingState<MessageTimelineCursor, MessageListItem>): MessageTimelineCursor? =
        state.anchorPosition?.let { state.closestItemToPosition(it)?.cursor }
}
