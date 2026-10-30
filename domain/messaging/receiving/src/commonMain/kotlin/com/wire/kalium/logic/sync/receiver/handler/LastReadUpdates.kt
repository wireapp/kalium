/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.sync.receiver.handler

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.user.UserId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private const val MAX_READ_OPERATION_SKEW_MINUTES = 5

fun isValidReadOperation(operationTime: Instant, messageDate: Instant): Boolean =
    operationTime.toEpochMilliseconds() >= 0 && operationTime <= messageDate + MAX_READ_OPERATION_SKEW_MINUTES.minutes

fun MessageContent.MarkUnread.isValidFor(messageDate: Instant): Boolean =
    unreadFrom.toEpochMilliseconds() >= 0 && unreadFrom <= messageDate && isValidReadOperation(operationTime, messageDate)

/** Shared authorization and gathering. The app flushes at its existing batch boundary. */
class LastReadUpdates(private val selfUserId: UserId) {
    private val dates = mutableMapOf<ConversationId, Instant>()
    private val mutex = Mutex()

    suspend fun add(message: Message.Signaling, content: MessageContent.LastRead, isSelfConversation: Boolean) {
        if (message.senderUserId != selfUserId || !isSelfConversation) return
        mutex.withLock {
            val current = dates[content.conversationId]
            if (current == null || content.time > current) dates[content.conversationId] = content.time
        }
    }

    suspend fun snapshot(): Map<ConversationId, Instant> = mutex.withLock { dates.toMap() }

    suspend fun persisted(pending: Map<ConversationId, Instant>) = mutex.withLock {
        pending.forEach { (id, date) -> if (dates[id] == date) dates.remove(id) }
    }
}
