/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.NotificationMessageKey
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.builtins.ListSerializer
import kotlin.time.Clock
import kotlin.time.Instant

/** Only identities are durable. Private preview content is rebuilt from the canonical account rows. */
@Serializable
internal data class PendingMessageAlert(
    @SerialName("message") val message: String,
    @SerialName("conversation") val conversation: String,
    @SerialName("domain") val domain: String,
    @SerialName("createdAt") val createdAt: Long,
) {
    fun key() = NotificationMessageKey(message, QualifiedIDEntity(conversation, domain))
}

/** The account's existing exclusive owner serializes app and NSE delivery. */
class PendingMessageAlerts internal constructor(
    private val database: UserDatabaseBuilder,
    private val selfUserId: UserId,
    private val now: () -> Instant = { Clock.System.now() },
) {
    /** Rechecks read/mute/archive/deletion/expiry and preview privacy on every attempt. */
    @Suppress("LoopWithTooManyJumpStatements") // Invalid pending identities are retired without exposing their content.
    suspend fun read(showContent: Boolean = true): List<ReceivedMessageResult> = database.writeMessageEffects {
        val records = read(metadata)
        val retained = mutableListOf<PendingMessageAlert>()
        val result = mutableListOf<ReceivedMessageResult>()
        val preference = UserConfigDataSource(prefs, config, lazy { error("Unused by notification preferences") })
            .getMessageContentPreference()
        val mapper = RegularMessageMapper(selfUserId)
        for (record in records) {
            if (now() - Instant.fromEpochMilliseconds(record.createdAt) > UnnotifiedMessageAlerts.MAX_AGE) continue
            val key = record.key()
            val currentId = messages.resolveNotificationMessage(key.messageId, key.conversationId) ?: key.messageId
            val message = messages.getMessageById(currentId, key.conversationId) as? MessageEntity.Regular ?: continue
            val conversation = conversations.getConversationById(key.conversationId) ?: continue
            // Commitment already advanced this cursor. Every other eligibility check still applies.
            val alert = storedMessagePresentation(
                message, conversation.copy(lastNotificationDate = null), mapper, selfUserId, messages, users,
            )?.takeIf { it.shouldNotify } ?: continue
            retained.add(record)
            result.add(alert.copy(messageId = record.message).notificationPreview(showContent, preference))
        }
        if (retained != records) write(metadata, retained)
        result
    }

    /** Call only after native enqueue succeeded, or the same identity is already delivered/pending in iOS. */
    suspend fun acknowledge(keys: List<NotificationMessageKey>) {
        if (keys.isEmpty()) return
        database.writeMessageEffects {
            val acknowledged = keys.toSet()
            write(metadata, read(metadata).filterNot { it.key() in acknowledged })
        }
    }

    internal companion object {
        const val KEY = "apple.pending-message-alerts.v1"
        private val serializer = ListSerializer(PendingMessageAlert.serializer())

        suspend fun append(metadata: MetadataDAO, alerts: List<ReceivedMessageResult>, now: Instant) {
            if (alerts.isEmpty()) return
            val pending = read(metadata).associateBy { it.key() }.toMutableMap()
            alerts.forEach { alert ->
                val record = PendingMessageAlert(
                    alert.messageId,
                    alert.conversationValue,
                    alert.conversationDomain,
                    now.toEpochMilliseconds(),
                )
                pending.getOrPut(record.key()) { record }
            }
            write(metadata, pending.values.toList())
        }

        private suspend fun read(metadata: MetadataDAO): List<PendingMessageAlert> =
            metadata.getSerializable(KEY, serializer).orEmpty()

        private suspend fun write(metadata: MetadataDAO, records: List<PendingMessageAlert>) {
            if (records.isEmpty()) metadata.deleteValue(KEY)
            else metadata.putSerializable(KEY, records, serializer)
        }
    }
}
