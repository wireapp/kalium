/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.db.MessageEffectDAOs
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.combine

/**
 * Message alerts not shown yet, selected like the Android app (ADR 11): unread messages newer than their
 * conversation's last notified date. Each alert is rebuilt with the receiving eligibility rules.
 */
class UnnotifiedMessageAlerts(
    private val database: UserDatabaseBuilder,
    val selfUserId: UserId,
    private val timeSource: TimeSource = TimeSource.Monotonic,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private val mapper = RegularMessageMapper(selfUserId)
    val pending = PendingMessageAlerts(database, selfUserId, now)

    /** Wake the current owner for new candidates and durable delivery retries; no polling or extra runtime. */
    fun observeChanges() = combine(
        database.messageDAO.observeNotificationMessages(),
        database.metadataDAO.valueByKeyFlow(PendingMessageAlerts.KEY),
    ) { _, _ -> Unit }

    /** An account-owner-local snapshot. Contains no preview text and must not be serialized for IPC. */
    class Prepared internal constructor(
        internal val owner: UnnotifiedMessageAlerts,
        internal val keys: Set<NotificationMessageKey>,
        internal val deadline: TimeMark,
        internal val showContent: Boolean,
    )

    sealed interface CommitResult {
        data class Committed(val alerts: List<ReceivedMessageResult>) : CommitResult
        data object Expired : CommitResult
        data object Stale : CommitResult
    }

    /** No notification state is changed until [commit]. Abandoned or expired snapshots need no cleanup. */
    suspend fun prepare(showContent: Boolean = true, lifetime: Duration = DEFAULT_LIFETIME): Prepared {
        require(lifetime > Duration.ZERO && lifetime <= MAX_LIFETIME)
        val deadline = timeSource.markNow() + lifetime
        val keys = database.messageDAO.getNotificationMessage().map { NotificationMessageKey(it.id, it.conversationId) }.toSet()
        return Prepared(this, keys, deadline, showContent)
    }

    /**
     * Claims still eligible messages in one database transaction. Overlapping commits cannot return the
     * same message. Rechecks privacy and eligibility at commitment, without claiming later arrivals.
     * The caller must be ready to present the result: this is a claim, not proof of OS delivery.
     */
    suspend fun commit(prepared: Prepared): CommitResult {
        require(prepared.owner === this)
        return database.writeMessageEffects {
            if (prepared.deadline.hasPassedNow()) return@writeMessageEffects CommitResult.Expired
            val current = messages.getNotificationMessage()
            val candidates = current.filter { NotificationMessageKey(it.id, it.conversationId) in prepared.keys }
            val dates = candidates.groupBy { it.conversationId }.mapValues { (_, unread) -> unread.maxOf { it.date } }
            if (hasUnpreparedOlderMessage(current, prepared, dates)) return@writeMessageEffects CommitResult.Stale
            val notBefore = now() - MAX_AGE
            val preference = UserConfigDataSource(prefs, config, lazy { error("Unused by notification preferences") })
                .getMessageContentPreference()
            val alerts = mutableListOf<ReceivedMessageResult>()
            for (candidate in candidates) {
                if (alerts.size == MAX_ALERTS || candidate.date < notBefore) break
                eligibleAlert(candidate)?.let { alerts.add(it.notificationPreview(prepared.showContent, preference)) }
            }
            currentCoroutineContext().ensureActive()
            if (prepared.deadline.hasPassedNow()) return@writeMessageEffects CommitResult.Expired
            candidates.groupBy { it.conversationId }.forEach { (conversationId, unread) ->
                conversations.updateConversationNotificationDate(conversationId, unread.maxOf { it.date })
            }
            messages.rememberNotificationMessages(
                alerts.map {
                    NotificationMessageKey(it.messageId, QualifiedIDEntity(it.conversationValue, it.conversationDomain))
                }
            )
            PendingMessageAlerts.append(metadata, alerts, now())
            CommitResult.Committed(alerts.asReversed())
        }
    }

    /** Immediate claiming for hosts that already own the account and are ready to present. */
    suspend fun take(): List<ReceivedMessageResult> = when (val result = commit(prepare())) {
        is CommitResult.Committed -> result.alerts
        CommitResult.Expired, CommitResult.Stale -> emptyList()
    }

    // A late, older event must not be skipped by advancing a conversation's date cursor.
    private fun hasUnpreparedOlderMessage(
        current: List<NotificationMessageEntity>,
        prepared: Prepared,
        dates: Map<QualifiedIDEntity, Instant>,
    ): Boolean = current.any { candidate ->
        NotificationMessageKey(candidate.id, candidate.conversationId) !in prepared.keys &&
            dates[candidate.conversationId]?.let { candidate.date <= it } == true
    }

    private suspend fun MessageEffectDAOs.eligibleAlert(candidate: NotificationMessageEntity): ReceivedMessageResult? {
        val message = messages.getMessageById(candidate.id, candidate.conversationId) as? MessageEntity.Regular
        val conversation = conversations.getConversationById(candidate.conversationId)
        if (message == null || conversation == null) return null
        return storedMessagePresentation(message, conversation, mapper, selfUserId, messages, users)?.takeIf { it.shouldNotify }
    }

    internal companion object {
        // Bounds alerts for messages the app received without marking them notified, such as before an upgrade.
        val MAX_AGE = 6.hours
        val DEFAULT_LIFETIME = 20.seconds
        val MAX_LIFETIME = 30.seconds
        const val MAX_ALERTS = 10
    }
}
