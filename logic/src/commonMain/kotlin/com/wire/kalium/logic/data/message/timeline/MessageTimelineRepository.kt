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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageTimelineDAO
import com.wire.kalium.persistence.dao.message.MessageTimelineSession

/** Account-scoped access to timeline sessions and paging sources. */
internal class MessageTimelineRepository(
    private val dao: MessageTimelineDAO,
    private val selfUserId: UserId,
) {
    val maxPageSize: Int get() = MessageTimelineSession.MAX_PAGE_SIZE

    fun open(conversationId: ConversationId, policy: MessageTimelinePolicy): MessageTimeline =
        MessageTimeline(dao.open(conversationId.toDao(), policy == MessageTimelinePolicy.DEFAULT), selfUserId)

    fun pagingSource(
        conversationId: ConversationId,
        policy: MessageTimelinePolicy,
        initialRequest: MessageTimelineRequest,
    ): MessageTimelinePagingSource = MessageTimelinePagingSource(dao, selfUserId, conversationId, policy, initialRequest)
}
