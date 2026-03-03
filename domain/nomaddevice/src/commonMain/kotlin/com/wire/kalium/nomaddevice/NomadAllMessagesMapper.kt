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

package com.wire.kalium.nomaddevice

import com.wire.kalium.network.api.authenticated.nomaddevice.NomadAllMessagesResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationWithMessages
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.backup.NomadMessageToInsert
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAttachment
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64

private const val EPOCH_MILLIS_THRESHOLD = 10_000_000_000L

internal data class NomadMappedMessages(
    val totalMessages: Int,
    val messages: List<NomadMessageToInsert>,
    val skippedMessages: Int,
)

internal class NomadAllMessagesMapper {

    fun map(
        response: NomadAllMessagesResponse,
    ): NomadMappedMessages {
        var skipped = 0
        val mappedMessages = response.conversations.flatMap { conversationWithMessages ->
            conversationWithMessages.messages.mapNotNull { storedMessage ->
                mapMessageOrNull(conversationWithMessages, storedMessage).also { mapped ->
                    if (mapped == null) skipped += 1
                }
            }
        }

        return NomadMappedMessages(
            totalMessages = response.conversations.sumOf { it.messages.size },
            messages = mappedMessages,
            skippedMessages = skipped
        )
    }

    @Suppress("ReturnCount")
    private fun mapMessageOrNull(
        conversationWithMessages: NomadConversationWithMessages,
        storedMessage: NomadStoredMessage,
    ): NomadMessageToInsert? {
        val payloadBytes = runCatching { Base64.Default.decode(storedMessage.payload) }.getOrElse {
            logSkip(storedMessage, conversationWithMessages, "invalid base64 payload")
            return null
        }

        val payload = runCatching { NomadDeviceMessagePayload.decodeFromByteArray(payloadBytes) }.getOrElse {
            logSkip(storedMessage, conversationWithMessages, "invalid protobuf payload")
            return null
        }

        val senderUserId = payload.senderUserId.toDaoQualifiedId()
        val creationDate = payload.creationDate.toInstantGuessingUnit()
        val lastEditDate = payload.lastEditTime?.let { Instant.fromEpochMilliseconds(it) }
        val content = payload.content.toSyncableMessageContent(
            senderUserId = senderUserId,
            senderClientId = payload.senderClientId,
            creationDate = creationDate,
            lastEditDate = lastEditDate,
        )
        val conversationId = conversationWithMessages.conversation.toDaoConversationId()

        return NomadMessageToInsert(
            id = storedMessage.messageId,
            conversationId = conversationId,
            date = storedMessage.timestamp.toInstantGuessingUnit(),
            payload = content,
        )
    }

    private fun NomadDeviceMessageContent.toSyncableMessageContent(
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        creationDate: Instant,
        lastEditDate: Instant?,
    ): SyncableMessagePayloadEntity =
        when (val contentValue = content) {
            is NomadDeviceMessageContent.Content.Text -> SyncableMessagePayloadEntity.Text(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                lastEditDate = lastEditDate,
                text = contentValue.value.text,
                mentions = contentValue.value.mentions.map { mention ->
                    MessageEntity.Mention(
                        start = mention.start,
                        length = mention.length,
                        userId = mention.userId.toDaoQualifiedId()
                    )
                },
                quotedMessageId = contentValue.value.quotedMessageId
            )

            is NomadDeviceMessageContent.Content.Asset -> contentValue.value.toSyncableAssetContent(
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                creationDate = creationDate,
                lastEditDate = lastEditDate,
            )

            is NomadDeviceMessageContent.Content.Location -> SyncableMessagePayloadEntity.Location(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                lastEditDate = lastEditDate,
                latitude = contentValue.value.latitude,
                longitude = contentValue.value.longitude,
                name = contentValue.value.name,
                zoom = contentValue.value.zoom
            )

            is NomadDeviceMessageContent.Content.Multipart -> SyncableMessagePayloadEntity.Multipart(
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                lastEditDate = lastEditDate,
                text = contentValue.value.text?.text,
                mentions = contentValue.value.text?.mentions.orEmpty().map { mention ->
                    MessageEntity.Mention(
                        start = mention.start,
                        length = mention.length,
                        userId = mention.userId.toDaoQualifiedId()
                    )
                },
                quotedMessageId = contentValue.value.text?.quotedMessageId,
                attachments = contentValue.value.attachments.mapNotNull { attachment ->
                    attachment.toMessageAttachmentOrNull()
                }
            )

            null -> SyncableMessagePayloadEntity.Unsupported(
                contentType = MessageEntity.ContentType.UNKNOWN,
                creationDate = creationDate,
                senderUserId = senderUserId,
                senderClientId = senderClientId,
                lastEditDate = lastEditDate,
            )
        }

    private fun NomadDeviceAsset.toSyncableAssetContent(
        senderUserId: QualifiedIDEntity,
        senderClientId: String?,
        creationDate: Instant,
        lastEditDate: Instant?,
    ): SyncableMessagePayloadEntity.Asset {
        val metadata = metaData
        return SyncableMessagePayloadEntity.Asset(
            creationDate = creationDate,
            senderUserId = senderUserId,
            senderClientId = senderClientId,
            lastEditDate = lastEditDate,
            mimeType = mimeType,
            size = size,
            name = name,
            otrKey = otrKey.array,
            sha256 = sha256.array,
            assetId = assetId,
            assetToken = assetToken,
            assetDomain = assetDomain,
            encryptionAlgorithm = encryption,
            width = metadata.widthOrNull(),
            height = metadata.heightOrNull(),
            durationMs = metadata.durationOrNull(),
            normalizedLoudness = metadata.normalizedLoudnessOrNull(),
        )
    }

    private fun NomadDeviceAttachment.toMessageAttachmentOrNull(): MessageAttachmentEntity? {
        val asset = (content as? NomadDeviceAttachment.Content.Asset)?.value ?: return null
        return MessageAttachmentEntity(
            assetId = asset.assetId,
            cellAsset = true,
            mimeType = asset.mimeType,
            assetPath = asset.name,
            assetSize = asset.size,
            localPath = null,
            previewUrl = null,
            assetWidth = asset.metaData.widthOrNull(),
            assetHeight = asset.metaData.heightOrNull(),
            assetDuration = asset.metaData.durationOrNull(),
            assetTransferStatus = AssetTransferStatusEntity.NOT_DOWNLOADED.name,
            contentUrl = null,
            contentHash = null,
            assetIndex = null,
            contentExpiresAt = null,
            isEditSupported = false
        )
    }

    private fun NomadDeviceAsset.MetaData<*>?.widthOrNull(): Int? = when (this) {
        is NomadDeviceAsset.MetaData.Image -> value.width
        is NomadDeviceAsset.MetaData.Video -> value.width
        else -> null
    }

    private fun NomadDeviceAsset.MetaData<*>?.heightOrNull(): Int? = when (this) {
        is NomadDeviceAsset.MetaData.Image -> value.height
        is NomadDeviceAsset.MetaData.Video -> value.height
        else -> null
    }

    private fun NomadDeviceAsset.MetaData<*>?.durationOrNull(): Long? = when (this) {
        is NomadDeviceAsset.MetaData.Video -> value.durationInMillis
        is NomadDeviceAsset.MetaData.Audio -> value.durationInMillis
        else -> null
    }

    private fun NomadDeviceAsset.MetaData<*>?.normalizedLoudnessOrNull(): ByteArray? = when (this) {
        is NomadDeviceAsset.MetaData.Audio -> value.normalization?.array
        else -> null
    }

    private fun logSkip(
        storedMessage: NomadStoredMessage,
        conversation: NomadConversationWithMessages,
        reason: String,
    ) {
        nomadLogger.w(
            "Skipping Nomad message '${storedMessage.messageId}' in conversation " +
                "'${conversation.conversation.id}@${conversation.conversation.domain}': $reason."
        )
    }

}

private fun Long.toInstantGuessingUnit(): Instant =
    if (this >= EPOCH_MILLIS_THRESHOLD) {
        Instant.fromEpochMilliseconds(this)
    } else {
        Instant.fromEpochSeconds(this)
    }

private fun Conversation.toDaoConversationId(): QualifiedIDEntity =
    QualifiedIDEntity(value = id, domain = domain)

private fun NomadDeviceQualifiedId.toDaoQualifiedId(): QualifiedIDEntity =
    QualifiedIDEntity(value = value, domain = domain)
