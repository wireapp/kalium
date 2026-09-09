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

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageTimelineCursorEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineLoadResultEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineRequestEntity
import com.wire.kalium.persistence.dao.message.MessageTimelineSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

public enum class MessageTimelinePolicy {
    /** Visible bubbles and deleted regular-message markers. */
    DEFAULT,

    /** Visible bubbles, excluding deleted markers. */
    VISIBLE_ONLY,
}

/** Ephemeral cursor issued by an account-scoped timeline. Do not persist it or reuse it after switching accounts. */
public class MessageTimelineCursor internal constructor(internal val entity: MessageTimelineCursorEntity) {
    public val messageId: String get() = entity.messageId
    public val date: Instant get() = entity.date
    public val conversationId: ConversationId get() = entity.conversationId.toModel()
}

public sealed interface MessageTimelineRequest {
    public data object Latest : MessageTimelineRequest
    public data class Older(public val cursor: MessageTimelineCursor) : MessageTimelineRequest
    public data class Newer(public val cursor: MessageTimelineCursor) : MessageTimelineRequest

    /** Refresh resolves the current tuple by ID, then uses lastKnownCursor if the message has gone. */
    public data class AroundMessage(
        public val messageId: String,
        public val lastKnownCursor: MessageTimelineCursor? = null,
    ) : MessageTimelineRequest

    /** The earliest eligible message strictly newer than lastReadDate, or latest when there is none. */
    public data class AtReadBoundary(public val lastReadDate: Instant) : MessageTimelineRequest
}

public data class MessageTimelinePage(
    public val items: List<MessageListItem>,
    public val olderCursor: MessageTimelineCursor?,
    public val newerCursor: MessageTimelineCursor?,
    public val anchorMessageId: String?,
)

public sealed interface MessageTimelineResult {
    public data class Success(public val page: MessageTimelinePage) : MessageTimelineResult
    public data object AnchorNotFound : MessageTimelineResult
    public data object InvalidCursor : MessageTimelineResult
    public data object InvalidLimit : MessageTimelineResult
    public data object Invalidated : MessageTimelineResult
    public data class Failure(public val error: StorageFailure) : MessageTimelineResult
}

/**
 * A concrete page API for Kotlin and Swift. All pages belong to this generation.
 * Observe invalidated, discard its pages when true, and open another timeline around the visible item's ID and cursor.
 * Close this session when leaving the conversation. Local exhaustion is independent of remote Nomad history.
 */
public class MessageTimeline internal constructor(
    private val session: MessageTimelineSession,
    selfUserId: UserId,
) : AutoCloseable {
    private val mapper = MessageListItemMapper(selfUserId)
    public val invalidated: StateFlow<Boolean> get() = session.invalidated

    /** Positive limits are capped at 100. Use 20 for ordinary pages and 60 for initial/around loads. */
    @Suppress("TooGenericExceptionCaught")
    public suspend fun load(request: MessageTimelineRequest, limit: Int = 20): MessageTimelineResult {
        if (limit <= 0) return MessageTimelineResult.InvalidLimit
        return try {
            when (val result = session.load(request.toEntity(), limit)) {
                is MessageTimelineLoadResultEntity.Success -> {
                    val page = mapper.map(result.page)
                    currentCoroutineContext().ensureActive()
                    if (invalidated.value) MessageTimelineResult.Invalidated else MessageTimelineResult.Success(page)
                }
                MessageTimelineLoadResultEntity.AnchorNotFound -> MessageTimelineResult.AnchorNotFound
                MessageTimelineLoadResultEntity.InvalidCursor -> MessageTimelineResult.InvalidCursor
                MessageTimelineLoadResultEntity.Invalidated -> MessageTimelineResult.Invalidated
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            MessageTimelineResult.Failure(StorageFailure.Generic(failure))
        }
    }

    public override fun close(): Unit = session.close()
}

internal fun MessageTimelineRequest.toEntity(): MessageTimelineRequestEntity = when (this) {
    MessageTimelineRequest.Latest -> MessageTimelineRequestEntity.Latest
    is MessageTimelineRequest.Older -> MessageTimelineRequestEntity.Older(cursor.entity)
    is MessageTimelineRequest.Newer -> MessageTimelineRequestEntity.Newer(cursor.entity)
    is MessageTimelineRequest.AroundMessage -> MessageTimelineRequestEntity.AroundMessage(messageId, lastKnownCursor?.entity)
    is MessageTimelineRequest.AtReadBoundary -> MessageTimelineRequestEntity.AtReadBoundary(lastReadDate)
}
