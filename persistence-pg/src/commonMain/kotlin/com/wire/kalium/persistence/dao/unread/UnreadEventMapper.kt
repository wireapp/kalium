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

package com.wire.kalium.persistence.dao.unread

import com.wire.kalium.persistence.dao.QualifiedIDEntity

object UnreadEventMapper {

    fun toUnreadEntity(
        conversationId: QualifiedIDEntity,
        type: UnreadEventTypeEntity,
    ): UnreadEventEntity {
        return UnreadEventEntity(
            type = type,
            conversationId = conversationId,
        )
    }

    @Suppress("LongParameterList")
    fun toConversationUnreadEntity(
        conversationId: QualifiedIDEntity,
        knocksCount: Long?,
        missedCallsCount: Long?,
        mentionsCount: Long?,
        repliesCount: Long?,
        messagesCount: Long?,
    ): ConversationUnreadEventEntity {

        return ConversationUnreadEventEntity(conversationId = conversationId,
            unreadEvents = mapOf<UnreadEventTypeEntity, Int>()
                .plus(UnreadEventTypeEntity.KNOCK to (knocksCount?.toInt() ?: 0))
                .plus(UnreadEventTypeEntity.MISSED_CALL to (missedCallsCount?.toInt() ?: 0))
                .plus(UnreadEventTypeEntity.MENTION to (mentionsCount?.toInt() ?: 0))
                .plus(UnreadEventTypeEntity.REPLY to (repliesCount?.toInt() ?: 0))
                .plus(UnreadEventTypeEntity.MESSAGE to (messagesCount?.toInt() ?: 0))
                .filterValues { it > 0 }
        )
    }

}
