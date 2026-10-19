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
import kotlin.time.Instant

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
