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

import com.wire.kalium.persistence.MessageMention
import com.wire.kalium.persistence.MessageRecipientFailure
import com.wire.kalium.persistence.TimelineAttachments
import com.wire.kalium.persistence.TimelinePreviews
import com.wire.kalium.persistence.TimelineReactions
import com.wire.kalium.persistence.TimelineRows
import com.wire.kalium.persistence.TimelineUsers
import com.wire.kalium.persistence.content.ButtonContent
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.datetime.Instant

/** A database tuple. Only the issuing account, conversation and display policy may use it. */
class MessageTimelineCursorEntity internal constructor(
    val messageId: String,
    val date: Instant,
    val conversationId: ConversationIDEntity,
    internal val scope: Any,
    internal val generation: Any,
    internal val includeDeleted: Boolean,
)

sealed interface MessageTimelineRequestEntity {
    data object Latest : MessageTimelineRequestEntity
    data class Older(val cursor: MessageTimelineCursorEntity) : MessageTimelineRequestEntity
    data class Newer(val cursor: MessageTimelineCursorEntity) : MessageTimelineRequestEntity
    data class AroundMessage(val messageId: String, val fallback: MessageTimelineCursorEntity? = null) : MessageTimelineRequestEntity
    data class AtReadBoundary(val lastReadDate: Instant) : MessageTimelineRequestEntity
}

/** Page-scoped rows. None of these queries hydrate MessageDetailsView or UserDetails. */
data class MessageTimelinePageEntity(
    val rows: List<TimelineRows>,
    val cursors: Map<String, MessageTimelineCursorEntity>,
    val olderCursor: MessageTimelineCursorEntity?,
    val newerCursor: MessageTimelineCursorEntity?,
    val anchorMessageId: String?,
    val mentions: Map<String, List<MessageMention>> = emptyMap(),
    val previews: Map<String, List<TimelinePreviews>> = emptyMap(),
    val buttons: Map<String, List<ButtonContent>> = emptyMap(),
    val reactions: Map<String, List<TimelineReactions>> = emptyMap(),
    val readCounts: Map<String, Long> = emptyMap(),
    val failures: Map<String, List<MessageRecipientFailure>> = emptyMap(),
    val attachments: Map<String, List<TimelineAttachments>> = emptyMap(),
    val systemContents: Map<String, MessageEntityContent.System> = emptyMap(),
    val users: Map<UserIDEntity, TimelineUsers> = emptyMap(),
)

sealed interface MessageTimelineLoadResultEntity {
    data class Success(val page: MessageTimelinePageEntity) : MessageTimelineLoadResultEntity
    data object AnchorNotFound : MessageTimelineLoadResultEntity
    data object InvalidCursor : MessageTimelineLoadResultEntity
    data object Invalidated : MessageTimelineLoadResultEntity
}
