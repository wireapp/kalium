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

import com.wire.kalium.persistence.MessageThreadsQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.datetime.Instant

/**
 * Must be called within a transaction.
 * Syncs the visibility of a thread item and adjusts the thread's visible reply count
 * when the visibility changes.
 */
internal fun MessageThreadsQueries.syncThreadItemVisibilityIfNeeded(
    conversationId: QualifiedIDEntity,
    messageId: String,
    newVisibility: MessageEntity.Visibility,
) {
    val previous = getThreadItemByMessageId(
        conversation_id = conversationId,
        message_id = messageId,
        mapper = ::ThreadItemVisibilityState
    ).executeAsOneOrNull() ?: return

    if (previous.visibility == newVisibility) return

    updateThreadItemVisibility(
        conversation_id = conversationId,
        message_id = messageId,
        visibility = newVisibility,
    )

    if (previous.isRoot) return

    when {
        previous.visibility == MessageEntity.Visibility.VISIBLE -> {
            decrementThreadVisibleReplyCount(
                conversation_id = conversationId,
                thread_id = previous.threadId
            )
        }

        newVisibility == MessageEntity.Visibility.VISIBLE -> {
            incrementThreadVisibleReplyCount(
                conversation_id = conversationId,
                thread_id = previous.threadId,
                reply_date = previous.creationDate
            )
        }
    }
}

private data class ThreadItemVisibilityState(
    val threadId: String,
    val isRoot: Boolean,
    val visibility: MessageEntity.Visibility,
    val creationDate: Instant,
)