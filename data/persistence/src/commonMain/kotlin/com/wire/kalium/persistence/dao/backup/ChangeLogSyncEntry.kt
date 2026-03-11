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

package com.wire.kalium.persistence.dao.backup

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionsSyncEntity
import com.wire.kalium.persistence.dao.receipt.MessageReadReceiptsSyncEntity
import kotlinx.datetime.Instant

/**
 * A remote-backup changelog event enriched with the data needed for proto encoding.
 * Each event type exposes only its relevant payload.
 */
sealed interface ChangeLogSyncEvent {
    val change: ChangeLogEntry

    data class MessageUpsert(
        val conversationId: QualifiedIDEntity,
        val messageId: String,
        override val change: ChangeLogEntry,
        val message: SyncableMessagePayloadEntity?,
    ) : ChangeLogSyncEvent

    data class MessageDelete(
        val conversationId: QualifiedIDEntity,
        val messageId: String,
        override val change: ChangeLogEntry,
    ) : ChangeLogSyncEvent

    data class ReactionsSync(
        val conversationId: QualifiedIDEntity,
        val messageId: String,
        override val change: ChangeLogEntry,
        val reactions: MessageReactionsSyncEntity,
    ) : ChangeLogSyncEvent

    data class ReadReceiptSync(
        val conversationId: QualifiedIDEntity,
        val messageId: String,
        override val change: ChangeLogEntry,
        val readReceipts: MessageReadReceiptsSyncEntity,
    ) : ChangeLogSyncEvent

    data class ConversationDelete(
        override val change: ChangeLogEntry,
    ) : ChangeLogSyncEvent

    data class ConversationClear(
        override val change: ChangeLogEntry,
    ) : ChangeLogSyncEvent
}

/**
 * Minimal message payload needed for nomad-device proto encoding.
 * This is intentionally smaller than MessageDetailsView and avoids UI-specific joins.
 */
sealed interface SyncableMessagePayloadEntity {
    val contentType: MessageEntity.ContentType
    val creationDate: Instant
    val senderUserId: QualifiedIDEntity
    val senderClientId: String?
    val lastEditDate: Instant?

    data class Text(
        override val creationDate: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String?,
        override val lastEditDate: Instant?,
        val text: String?,
        val quotedMessageId: String?,
        val mentions: List<MessageEntity.Mention>,
    ) : SyncableMessagePayloadEntity {
        override val contentType: MessageEntity.ContentType = MessageEntity.ContentType.TEXT
    }

    data class Multipart(
        override val creationDate: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String?,
        override val lastEditDate: Instant?,
        val text: String?,
        val quotedMessageId: String?,
        val mentions: List<MessageEntity.Mention>,
        val attachments: List<MessageAttachmentEntity>,
    ) : SyncableMessagePayloadEntity {
        override val contentType: MessageEntity.ContentType = MessageEntity.ContentType.MULTIPART
    }

    data class Location(
        override val creationDate: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String?,
        override val lastEditDate: Instant?,
        val longitude: Float?,
        val latitude: Float?,
        val name: String?,
        val zoom: Int?,
    ) : SyncableMessagePayloadEntity {
        override val contentType: MessageEntity.ContentType = MessageEntity.ContentType.LOCATION
    }

    data class Asset(
        override val creationDate: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String?,
        override val lastEditDate: Instant?,
        val mimeType: String?,
        val size: Long?,
        val name: String?,
        val otrKey: ByteArray?,
        val sha256: ByteArray?,
        val assetId: String?,
        val assetToken: String?,
        val assetDomain: String?,
        val encryptionAlgorithm: String?,
        val width: Int?,
        val height: Int?,
        val durationMs: Long?,
        val normalizedLoudness: ByteArray?,
    ) : SyncableMessagePayloadEntity {
        override val contentType: MessageEntity.ContentType = MessageEntity.ContentType.ASSET

        @Suppress("CyclomaticComplexMethod")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Asset) return false

            if (creationDate != other.creationDate) return false
            if (senderUserId != other.senderUserId) return false
            if (senderClientId != other.senderClientId) return false
            if (lastEditDate != other.lastEditDate) return false
            if (mimeType != other.mimeType) return false
            if (size != other.size) return false
            if (name != other.name) return false
            if (!otrKey.contentEquals(other.otrKey)) return false
            if (!sha256.contentEquals(other.sha256)) return false
            if (assetId != other.assetId) return false
            if (assetToken != other.assetToken) return false
            if (assetDomain != other.assetDomain) return false
            if (encryptionAlgorithm != other.encryptionAlgorithm) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (durationMs != other.durationMs) return false
            if (!normalizedLoudness.contentEquals(other.normalizedLoudness)) return false

            return true
        }

        @Suppress("CyclomaticComplexMethod")
        override fun hashCode(): Int {
            var result = creationDate.hashCode()
            result = 31 * result + senderUserId.hashCode()
            result = 31 * result + (senderClientId?.hashCode() ?: 0)
            result = 31 * result + (lastEditDate?.hashCode() ?: 0)
            result = 31 * result + (mimeType?.hashCode() ?: 0)
            result = 31 * result + (size?.hashCode() ?: 0)
            result = 31 * result + (name?.hashCode() ?: 0)
            result = 31 * result + (otrKey?.contentHashCode() ?: 0)
            result = 31 * result + (sha256?.contentHashCode() ?: 0)
            result = 31 * result + (assetId?.hashCode() ?: 0)
            result = 31 * result + (assetToken?.hashCode() ?: 0)
            result = 31 * result + (assetDomain?.hashCode() ?: 0)
            result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
            result = 31 * result + (width ?: 0)
            result = 31 * result + (height ?: 0)
            result = 31 * result + (durationMs?.hashCode() ?: 0)
            result = 31 * result + (normalizedLoudness?.contentHashCode() ?: 0)
            return result
        }
    }

    data class Unsupported(
        override val contentType: MessageEntity.ContentType,
        override val creationDate: Instant,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String?,
        override val lastEditDate: Instant?,
    ) : SyncableMessagePayloadEntity
}
