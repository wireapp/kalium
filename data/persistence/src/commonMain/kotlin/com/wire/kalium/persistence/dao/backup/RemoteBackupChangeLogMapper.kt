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
import com.wire.kalium.persistence.dao.receipt.MessageReadReceiptsSyncEntity
import com.wire.kalium.persistence.dao.receipt.UserReadReceiptSyncEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionsSyncEntity
import com.wire.kalium.persistence.dao.reaction.UserReactionsSyncEntity
import kotlinx.datetime.Instant

internal object RemoteBackupChangeLogMapper {

    @Suppress("FunctionParameterNaming")
    fun toChangeLogEntry(
        conversation_id: QualifiedIDEntity,
        message_id: String?,
        event_type: ChangeLogEventType,
        timestamp_ms: Long,
        message_timestamp_ms: Long,
    ): ChangeLogEntry =
        ChangeLogEntry(
            conversationId = conversation_id,
            messageId = message_id,
            eventType = event_type,
            timestampMs = timestamp_ms,
            messageTimestampMs = message_timestamp_ms
        )

    @Suppress("FunctionParameterNaming")
    fun toConversationLastReadSyncEntity(
        conversation_id: QualifiedIDEntity,
        last_read_date: Instant,
    ): ConversationLastReadSyncEntity =
        ConversationLastReadSyncEntity(
            conversationId = conversation_id,
            lastReadDate = last_read_date
        )

    @Suppress("LongParameterList", "FunctionParameterNaming")
    fun toChangeLogSyncEvent(
        conversation_id: QualifiedIDEntity,
        message_id: String?,
        event_type: ChangeLogEventType,
        timestamp_ms: Long,
        message_timestamp_ms: Long,
        sync_message_id: String?,
        sync_conversation_id: QualifiedIDEntity?,
        sync_content_type: MessageEntity.ContentType?,
        sync_creation_date: Instant?,
        sync_sender_user_id: QualifiedIDEntity?,
        sync_sender_client_id: String?,
        sync_last_edit_date: Instant?,
        sync_text: String?,
        sync_quoted_message_id: String?,
        sync_mentions_json: String?,
        sync_attachments_json: String?,
        sync_asset_mime_type: String?,
        sync_asset_size: Long?,
        sync_asset_name: String?,
        sync_asset_otr_key: ByteArray?,
        sync_asset_sha256: ByteArray?,
        sync_asset_id: String?,
        sync_asset_token: String?,
        sync_asset_domain: String?,
        sync_asset_encryption_algorithm: String?,
        sync_asset_width: Int?,
        sync_asset_height: Int?,
        sync_asset_duration_ms: Long?,
        sync_asset_normalized_loudness: ByteArray?,
        sync_location_longitude: Float?,
        sync_location_latitude: Float?,
        sync_location_name: String?,
        sync_location_zoom: Int?,
        reactions_sync_raw: String?,
        read_receipts_sync_raw: String?,
    ): ChangeLogSyncEvent {
        val change = toChangeLogEntry(
            conversation_id = conversation_id,
            message_id = message_id,
            event_type = event_type,
            timestamp_ms = timestamp_ms,
            message_timestamp_ms = message_timestamp_ms
        )

        val messagePayload = MessagePayloadColumns(
            syncMessageId = sync_message_id,
            syncConversationId = sync_conversation_id,
            syncContentType = sync_content_type,
            syncCreationDate = sync_creation_date,
            syncSenderUserId = sync_sender_user_id,
            syncSenderClientId = sync_sender_client_id,
            syncLastEditDate = sync_last_edit_date,
            syncText = sync_text,
            syncQuotedMessageId = sync_quoted_message_id,
            syncMentionsJson = sync_mentions_json,
            syncAttachmentsJson = sync_attachments_json,
            syncAssetMimeType = sync_asset_mime_type,
            syncAssetSize = sync_asset_size,
            syncAssetName = sync_asset_name,
            syncAssetOtrKey = sync_asset_otr_key,
            syncAssetSha256 = sync_asset_sha256,
            syncAssetId = sync_asset_id,
            syncAssetToken = sync_asset_token,
            syncAssetDomain = sync_asset_domain,
            syncAssetEncryptionAlgorithm = sync_asset_encryption_algorithm,
            syncAssetWidth = sync_asset_width,
            syncAssetHeight = sync_asset_height,
            syncAssetDurationMs = sync_asset_duration_ms,
            syncAssetNormalizedLoudness = sync_asset_normalized_loudness,
            syncLocationLongitude = sync_location_longitude,
            syncLocationLatitude = sync_location_latitude,
            syncLocationName = sync_location_name,
            syncLocationZoom = sync_location_zoom,
        ).toSyncableMessagePayload()

        return mapChangeLogSyncEvent(
            eventType = event_type,
            context = ChangeLogSyncEventContext(
                conversationId = conversation_id,
                messageId = message_id,
                change = change,
                messagePayload = messagePayload,
                reactionsSyncRaw = reactions_sync_raw,
                readReceiptsSyncRaw = read_receipts_sync_raw
            )
        )
    }

    private fun mapChangeLogSyncEvent(
        eventType: ChangeLogEventType,
        context: ChangeLogSyncEventContext,
    ): ChangeLogSyncEvent = when (eventType) {
        ChangeLogEventType.MESSAGE_UPSERT -> {
            val nonNullMessageId = context.messageId.requireField("message_id")
            ChangeLogSyncEvent.MessageUpsert(
                conversationId = context.conversationId,
                messageId = nonNullMessageId,
                change = context.change,
                message = context.messagePayload
            )
        }

        ChangeLogEventType.MESSAGE_DELETE -> {
            val nonNullMessageId = context.messageId.requireField("message_id")
            ChangeLogSyncEvent.MessageDelete(
                conversationId = context.conversationId,
                messageId = nonNullMessageId,
                change = context.change
            )
        }

        ChangeLogEventType.REACTIONS_SYNC -> {
            val nonNullMessageId = context.messageId.requireField("message_id")
            ChangeLogSyncEvent.ReactionsSync(
                conversationId = context.conversationId,
                messageId = nonNullMessageId,
                change = context.change,
                reactions = parseReactionsSyncEntity(
                    conversationId = context.conversationId,
                    messageId = nonNullMessageId,
                    raw = context.reactionsSyncRaw
                )
            )
        }

        ChangeLogEventType.READ_RECEIPT_SYNC -> {
            val nonNullMessageId = context.messageId.requireField("message_id")
            ChangeLogSyncEvent.ReadReceiptSync(
                conversationId = context.conversationId,
                messageId = nonNullMessageId,
                change = context.change,
                readReceipts = parseReadReceiptsSyncEntity(
                    conversationId = context.conversationId,
                    messageId = nonNullMessageId,
                    raw = context.readReceiptsSyncRaw
                )
            )
        }

        ChangeLogEventType.CONVERSATION_DELETE -> ChangeLogSyncEvent.ConversationDelete(change = context.change)
        ChangeLogEventType.CONVERSATION_CLEAR -> ChangeLogSyncEvent.ConversationClear(change = context.change)
    }

    @Suppress("LongMethod")
    private fun MessagePayloadColumns.toSyncableMessagePayload(): SyncableMessagePayloadEntity? {
        if (syncMessageId == null) return null

        syncConversationId.requireField("sync_conversation_id")
        val contentType = syncContentType.requireField("sync_content_type")
        val creationDate = syncCreationDate.requireField("sync_creation_date")
        val senderUserId = syncSenderUserId.requireField("sync_sender_user_id")

        return when (contentType) {
            MessageEntity.ContentType.TEXT -> SyncableMessagePayloadEntity.Text(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = syncSenderClientId,
                lastEditDate = syncLastEditDate,
                text = syncText,
                quotedMessageId = syncQuotedMessageId,
                mentionsJson = syncMentionsJson ?: "[]",
            )

            MessageEntity.ContentType.ASSET -> SyncableMessagePayloadEntity.Asset(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = syncSenderClientId,
                lastEditDate = syncLastEditDate,
                mimeType = syncAssetMimeType,
                size = syncAssetSize,
                name = syncAssetName,
                otrKey = syncAssetOtrKey,
                sha256 = syncAssetSha256,
                assetId = syncAssetId,
                assetToken = syncAssetToken,
                assetDomain = syncAssetDomain,
                encryptionAlgorithm = syncAssetEncryptionAlgorithm,
                width = syncAssetWidth,
                height = syncAssetHeight,
                durationMs = syncAssetDurationMs,
                normalizedLoudness = syncAssetNormalizedLoudness,
            )

            MessageEntity.ContentType.LOCATION -> SyncableMessagePayloadEntity.Location(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = syncSenderClientId,
                lastEditDate = syncLastEditDate,
                longitude = syncLocationLongitude,
                latitude = syncLocationLatitude,
                name = syncLocationName,
                zoom = syncLocationZoom,
            )

            MessageEntity.ContentType.MULTIPART -> SyncableMessagePayloadEntity.Multipart(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = syncSenderClientId,
                lastEditDate = syncLastEditDate,
                text = syncText,
                quotedMessageId = syncQuotedMessageId,
                mentionsJson = syncMentionsJson ?: "[]",
                attachmentsJson = syncAttachmentsJson ?: "[]",
            )

            else -> SyncableMessagePayloadEntity.Unsupported(
                contentType = contentType,
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = syncSenderClientId,
                lastEditDate = syncLastEditDate,
            )
        }
    }

    private data class ChangeLogSyncEventContext(
        val conversationId: QualifiedIDEntity,
        val messageId: String?,
        val change: ChangeLogEntry,
        val messagePayload: SyncableMessagePayloadEntity?,
        val reactionsSyncRaw: String?,
        val readReceiptsSyncRaw: String?,
    )

    @Suppress("LongParameterList")
    private data class MessagePayloadColumns(
        val syncMessageId: String?,
        val syncConversationId: QualifiedIDEntity?,
        val syncContentType: MessageEntity.ContentType?,
        val syncCreationDate: Instant?,
        val syncSenderUserId: QualifiedIDEntity?,
        val syncSenderClientId: String?,
        val syncLastEditDate: Instant?,
        val syncText: String?,
        val syncQuotedMessageId: String?,
        val syncMentionsJson: String?,
        val syncAttachmentsJson: String?,
        val syncAssetMimeType: String?,
        val syncAssetSize: Long?,
        val syncAssetName: String?,
        val syncAssetOtrKey: ByteArray?,
        val syncAssetSha256: ByteArray?,
        val syncAssetId: String?,
        val syncAssetToken: String?,
        val syncAssetDomain: String?,
        val syncAssetEncryptionAlgorithm: String?,
        val syncAssetWidth: Int?,
        val syncAssetHeight: Int?,
        val syncAssetDurationMs: Long?,
        val syncAssetNormalizedLoudness: ByteArray?,
        val syncLocationLongitude: Float?,
        val syncLocationLatitude: Float?,
        val syncLocationName: String?,
        val syncLocationZoom: Int?,
    ) {
        @Suppress("CyclomaticComplexMethod")
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other == null || this::class != other::class) return false

            other as MessagePayloadColumns

            if (syncAssetSize != other.syncAssetSize) return false
            if (syncAssetWidth != other.syncAssetWidth) return false
            if (syncAssetHeight != other.syncAssetHeight) return false
            if (syncAssetDurationMs != other.syncAssetDurationMs) return false
            if (syncLocationLongitude != other.syncLocationLongitude) return false
            if (syncLocationLatitude != other.syncLocationLatitude) return false
            if (syncLocationZoom != other.syncLocationZoom) return false
            if (syncMessageId != other.syncMessageId) return false
            if (syncConversationId != other.syncConversationId) return false
            if (syncContentType != other.syncContentType) return false
            if (syncCreationDate != other.syncCreationDate) return false
            if (syncSenderUserId != other.syncSenderUserId) return false
            if (syncSenderClientId != other.syncSenderClientId) return false
            if (syncLastEditDate != other.syncLastEditDate) return false
            if (syncText != other.syncText) return false
            if (syncQuotedMessageId != other.syncQuotedMessageId) return false
            if (syncMentionsJson != other.syncMentionsJson) return false
            if (syncAttachmentsJson != other.syncAttachmentsJson) return false
            if (syncAssetMimeType != other.syncAssetMimeType) return false
            if (syncAssetName != other.syncAssetName) return false
            if (!syncAssetOtrKey.contentEquals(other.syncAssetOtrKey)) return false
            if (!syncAssetSha256.contentEquals(other.syncAssetSha256)) return false
            if (syncAssetId != other.syncAssetId) return false
            if (syncAssetToken != other.syncAssetToken) return false
            if (syncAssetDomain != other.syncAssetDomain) return false
            if (syncAssetEncryptionAlgorithm != other.syncAssetEncryptionAlgorithm) return false
            if (!syncAssetNormalizedLoudness.contentEquals(other.syncAssetNormalizedLoudness)) return false
            if (syncLocationName != other.syncLocationName) return false

            return true
        }

        @Suppress("CyclomaticComplexMethod")
        override fun hashCode(): Int {
            var result = syncAssetSize?.hashCode() ?: 0
            result = 31 * result + (syncAssetWidth ?: 0)
            result = 31 * result + (syncAssetHeight ?: 0)
            result = 31 * result + (syncAssetDurationMs?.hashCode() ?: 0)
            result = 31 * result + (syncLocationLongitude?.hashCode() ?: 0)
            result = 31 * result + (syncLocationLatitude?.hashCode() ?: 0)
            result = 31 * result + (syncLocationZoom ?: 0)
            result = 31 * result + (syncMessageId?.hashCode() ?: 0)
            result = 31 * result + (syncConversationId?.hashCode() ?: 0)
            result = 31 * result + (syncContentType?.hashCode() ?: 0)
            result = 31 * result + (syncCreationDate?.hashCode() ?: 0)
            result = 31 * result + (syncSenderUserId?.hashCode() ?: 0)
            result = 31 * result + (syncSenderClientId?.hashCode() ?: 0)
            result = 31 * result + (syncLastEditDate?.hashCode() ?: 0)
            result = 31 * result + (syncText?.hashCode() ?: 0)
            result = 31 * result + (syncQuotedMessageId?.hashCode() ?: 0)
            result = 31 * result + (syncMentionsJson?.hashCode() ?: 0)
            result = 31 * result + (syncAttachmentsJson?.hashCode() ?: 0)
            result = 31 * result + (syncAssetMimeType?.hashCode() ?: 0)
            result = 31 * result + (syncAssetName?.hashCode() ?: 0)
            result = 31 * result + (syncAssetOtrKey?.contentHashCode() ?: 0)
            result = 31 * result + (syncAssetSha256?.contentHashCode() ?: 0)
            result = 31 * result + (syncAssetId?.hashCode() ?: 0)
            result = 31 * result + (syncAssetToken?.hashCode() ?: 0)
            result = 31 * result + (syncAssetDomain?.hashCode() ?: 0)
            result = 31 * result + (syncAssetEncryptionAlgorithm?.hashCode() ?: 0)
            result = 31 * result + (syncAssetNormalizedLoudness?.contentHashCode() ?: 0)
            result = 31 * result + (syncLocationName?.hashCode() ?: 0)
            return result
        }
    }

    private fun parseReactionsSyncEntity(
        conversationId: QualifiedIDEntity,
        messageId: String,
        raw: String?,
    ): MessageReactionsSyncEntity {
        if (raw.isNullOrEmpty()) {
            return MessageReactionsSyncEntity(messageId = messageId, conversationId = conversationId, reactionsByUser = emptyList())
        }

        val reactionsByUser = raw.split("|")
            .mapNotNull { token ->
                val separatorIndex = token.indexOf(':')
                if (separatorIndex <= 0 || separatorIndex == token.lastIndex) return@mapNotNull null
                val userId = parseQualifiedId(token.substring(0, separatorIndex)) ?: return@mapNotNull null
                val emoji = token.substring(separatorIndex + 1)
                userId to emoji
            }
            .groupBy(
                keySelector = { (userId, _) -> userId },
                valueTransform = { (_, emoji) -> emoji }
            )
            .map { (userId, emojis) ->
                UserReactionsSyncEntity(
                    userId = userId,
                    emojis = emojis.toSet()
                )
            }
            .sortedBy { "${it.userId.value}@${it.userId.domain}" }

        return MessageReactionsSyncEntity(
            messageId = messageId,
            conversationId = conversationId,
            reactionsByUser = reactionsByUser
        )
    }

    private fun parseReadReceiptsSyncEntity(
        conversationId: QualifiedIDEntity,
        messageId: String,
        raw: String?,
    ): MessageReadReceiptsSyncEntity {
        if (raw.isNullOrEmpty()) {
            return MessageReadReceiptsSyncEntity(messageId = messageId, conversationId = conversationId, receipts = emptyList())
        }

        val receipts = raw.split("|")
            .mapNotNull { token ->
                val separatorIndex = token.indexOf(':')
                if (separatorIndex <= 0 || separatorIndex == token.lastIndex) return@mapNotNull null
                val userId = parseQualifiedId(token.substring(0, separatorIndex)) ?: return@mapNotNull null
                val date = runCatching { Instant.parse(token.substring(separatorIndex + 1)) }.getOrNull() ?: return@mapNotNull null
                UserReadReceiptSyncEntity(
                    userId = userId,
                    date = date
                )
            }
            .sortedWith(
                compareBy<UserReadReceiptSyncEntity> { "${it.userId.value}@${it.userId.domain}" }
                    .thenBy { it.date.toEpochMilliseconds() }
            )

        return MessageReadReceiptsSyncEntity(
            messageId = messageId,
            conversationId = conversationId,
            receipts = receipts
        )
    }

    private fun parseQualifiedId(value: String): QualifiedIDEntity? {
        val separatorIndex = value.indexOf('@')
        if (separatorIndex <= 0 || separatorIndex == value.lastIndex) return null
        return QualifiedIDEntity(
            value = value.substring(0, separatorIndex),
            domain = value.substring(separatorIndex + 1)
        )
    }

    private fun <T> T?.requireField(fieldName: String): T = requireNotNull(this) {
        "Field '$fieldName' is missing in changelog payload query."
    }
}
