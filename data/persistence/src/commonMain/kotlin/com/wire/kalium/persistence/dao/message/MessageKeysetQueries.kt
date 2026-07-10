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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import kotlinx.datetime.Instant

internal fun pendingKeysetQueries(
    queries: MessagesQueries,
    mapper: MessageMapper,
    conversationId: ConversationIDEntity,
    visibilities: Collection<MessageEntity.Visibility>,
) = MessageSegmentQueries(
    first = { limit ->
        queries.selectFirstPendingByConversationIdAndVisibility(
            conversationId,
            visibilities,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    from = { cursor, limit ->
        queries.selectPendingByConversationIdAndVisibilityFrom(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    after = { cursor, limit ->
        queries.selectPendingByConversationIdAndVisibilityAfter(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    before = { cursor, limit ->
        queries.selectPendingByConversationIdAndVisibilityBefore(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
)

internal fun nonPendingKeysetQueries(
    queries: MessagesQueries,
    mapper: MessageMapper,
    conversationId: ConversationIDEntity,
    visibilities: Collection<MessageEntity.Visibility>,
) = MessageSegmentQueries(
    first = { limit ->
        queries.selectFirstNonPendingByConversationIdAndVisibility(
            conversationId,
            visibilities,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    from = { cursor, limit ->
        queries.selectNonPendingByConversationIdAndVisibilityFrom(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    after = { cursor, limit ->
        queries.selectNonPendingByConversationIdAndVisibilityAfter(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
    before = { cursor, limit ->
        queries.selectNonPendingByConversationIdAndVisibilityBefore(
            conversationId,
            visibilities,
            cursor.date,
            cursor.id,
            limit,
            mapper::toEntityMessageFromView
        )
    },
)

internal fun MessageEntity.toCursor() = MessageCursor(
    segment = status.toCursorSegment(),
    date = date,
    id = id,
)

internal fun messageCursor(
    id: String,
    date: Instant,
    status: MessageEntity.Status,
) = MessageCursor(
    segment = status.toCursorSegment(),
    date = date,
    id = id,
)

private fun MessageEntity.Status.toCursorSegment() =
    if (this == MessageEntity.Status.PENDING) {
        MessageCursor.Segment.PENDING
    } else {
        MessageCursor.Segment.NON_PENDING
    }
