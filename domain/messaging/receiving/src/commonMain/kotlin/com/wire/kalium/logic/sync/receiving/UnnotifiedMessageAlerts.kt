/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * Message alerts not shown yet, selected like the Android app (ADR 11): unread messages newer than their
 * conversation's last notified date. Each alert is rebuilt with the receiving eligibility rules.
 */
class UnnotifiedMessageAlerts(
    database: UserDatabaseBuilder,
    private val selfUserId: UserId,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val messages = database.messageDAO
    private val conversations = database.conversationDAO
    private val users = database.userDAO
    private val mapper = RegularMessageMapper(selfUserId)

    /**
     * Returns the newest eligible alerts, oldest first, and marks every candidate notified so a later
     * attempt does not repeat them. Older messages remain unread in the app without an alert.
     */
    suspend fun take(): List<ReceivedMessageResult> {
        val candidates = messages.getNotificationMessage()
        val notBefore = now() - MAX_AGE
        val alerts = mutableListOf<ReceivedMessageResult>()
        // Notification.sq returns the newest first.
        for (candidate in candidates) {
            if (alerts.size == MAX_ALERTS || candidate.date < notBefore) break
            eligibleAlert(candidate)?.let(alerts::add)
        }
        // Each conversation advances only to its own newest unread message, never past messages not yet received.
        candidates.groupBy { it.conversationId }.forEach { (conversationId, unread) ->
            conversations.updateConversationNotificationDate(conversationId, unread.maxOf { it.date })
        }
        return alerts.asReversed()
    }

    private suspend fun eligibleAlert(candidate: NotificationMessageEntity): ReceivedMessageResult? {
        val message = messages.getMessageById(candidate.id, candidate.conversationId) as? MessageEntity.Regular
        val conversation = conversations.getConversationById(candidate.conversationId)
        if (message == null || conversation == null) return null
        return storedMessagePresentation(message, conversation, mapper, selfUserId, messages, users)?.takeIf { it.shouldNotify }
    }

    internal companion object {
        // Bounds alerts for messages the app received without marking them notified, such as before an upgrade.
        val MAX_AGE = 6.hours
        const val MAX_ALERTS = 10
    }
}
