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

import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.LastRead
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncEvent
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionsSyncEntity
import com.wire.kalium.persistence.dao.reaction.UserReactionsSyncEntity
import com.wire.kalium.persistence.dao.receipt.MessageReadReceiptsSyncEntity
import com.wire.kalium.persistence.dao.receipt.UserReadReceiptSyncEntity
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAttachment
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAudioMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceGenericMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceImageMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceLocation
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMention
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMultipart
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceText
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceVideoMetaData
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pbandk.ByteArr
import pbandk.encodeToByteArray
import kotlin.io.encoding.Base64

internal class NomadRemoteBackupChangeLogEventMapper {
    fun mapBatchToApiEvents(batch: ChangeLogSyncBatch): List<NomadMessageEvent> {
        val events = batch.events.mapNotNull { it.toApiEventOrNull() }.toMutableList()
        val lastReads = batch.conversationLastReads.map {
            LastRead(
                conversationId = it.conversationId.toString(),
                lastReadTimestamp = it.lastReadTimestampMs
            )
        }
        if (lastReads.isNotEmpty()) {
            events += NomadMessageEvent.LastReadEvent(lastRead = lastReads)
        }
        return events
    }

    private fun ChangeLogSyncEvent.toApiEventOrNull(): NomadMessageEvent? = when (this) {
        is ChangeLogSyncEvent.MessageUpsert -> toUpsertEventOrNull()
        is ChangeLogSyncEvent.MessageDelete -> NomadMessageEvent.DeleteMessageEvent(
            conversation = conversationId.toApiConversation(),
            messageId = messageId
        )

        is ChangeLogSyncEvent.ReactionsSync -> NomadMessageEvent.UpsertMessageStatusEvent(
            messageId = messageId,
            conversation = conversationId.toApiConversation(),
            reaction = reactions.toReactionJsonElement()
        )

        is ChangeLogSyncEvent.ReadReceiptSync -> NomadMessageEvent.UpsertMessageStatusEvent(
            messageId = messageId,
            conversation = conversationId.toApiConversation(),
            readReceipt = readReceipts.toReadReceiptJsonElement()
        )

        is ChangeLogSyncEvent.ConversationDelete -> NomadMessageEvent.WipeConversationEvent(
            conversation = change.conversationId.toApiConversation(),
            wipeMetaData = true
        )

        is ChangeLogSyncEvent.ConversationClear -> NomadMessageEvent.WipeConversationEvent(
            conversation = change.conversationId.toApiConversation(),
            wipeMetaData = false
        )
    }

    private fun ChangeLogSyncEvent.MessageUpsert.toUpsertEventOrNull(): NomadMessageEvent.UpsertMessageEvent? {
        val payloadModel = message?.toNomadDevicePayloadOrNull() ?: run {
            nomadLogger.w(
                "Skipping MESSAGE_UPSERT for conversation '${conversationId.toLogString()}' and message '$messageId': " +
                    "missing syncable message payload."
            )
            return null
        }
        val payloadBase64 = Base64.Default.encode(payloadModel.encodeToByteArray())
        return NomadMessageEvent.UpsertMessageEvent(
            messageId = messageId,
            conversation = conversationId.toApiConversation(),
            timestamp = change.messageTimestampMs,
            payload = payloadBase64
        )
    }

    private fun SyncableMessagePayloadEntity.toNomadDevicePayloadOrNull(): NomadDeviceMessagePayload? {
        val content = toNomadDeviceContentOrNull() ?: return null
        return NomadDeviceMessagePayload(
            senderUserId = senderUserId.toNomadDeviceQualifiedId(),
            senderClientId = senderClientId.orEmpty(),
            creationDate = creationDate.toEpochMilliseconds(),
            content = content,
            lastEditTime = lastEditDate?.toEpochMilliseconds()
        )
    }

    @Suppress("LongMethod")
    private fun SyncableMessagePayloadEntity.toNomadDeviceContentOrNull(): NomadDeviceMessageContent? = when (this) {
        is SyncableMessagePayloadEntity.Text -> NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Text(
                NomadDeviceText(
                    text = text.orEmpty(),
                    mentions = mentions.toNomadDeviceMentions(),
                    quotedMessageId = quotedMessageId
                )
            )
        )

        is SyncableMessagePayloadEntity.Asset -> {
            val mime = mimeType ?: return null
            val sizeValue = size ?: return null
            val otrKeyValue = otrKey ?: return null
            val sha256Value = sha256 ?: return null
            val remoteAssetId = assetId ?: return null
            NomadDeviceMessageContent(
                content = NomadDeviceMessageContent.Content.Asset(
                    NomadDeviceAsset(
                        mimeType = mime,
                        size = sizeValue,
                        name = name,
                        otrKey = ByteArr(otrKeyValue),
                        sha256 = ByteArr(sha256Value),
                        assetId = remoteAssetId,
                        assetToken = assetToken,
                        assetDomain = assetDomain,
                        encryption = encryptionAlgorithm,
                        metaData = when {
                            durationMs != null && (width != null || height != null) ->
                                NomadDeviceAsset.MetaData.Video(
                                    NomadDeviceVideoMetaData(
                                        width = width,
                                        height = height,
                                        durationInMillis = durationMs
                                    )
                                )

                            durationMs != null ->
                                NomadDeviceAsset.MetaData.Audio(
                                    NomadDeviceAudioMetaData(
                                        durationInMillis = durationMs,
                                        normalization = normalizedLoudness?.let { ByteArr(it) }
                                    )
                                )

                            width != null || height != null ->
                                NomadDeviceAsset.MetaData.Image(
                                    NomadDeviceImageMetaData(
                                        width = width ?: 0,
                                        height = height ?: 0
                                    )
                                )

                            name != null ->
                                NomadDeviceAsset.MetaData.Generic(
                                    NomadDeviceGenericMetaData(name = name)
                                )

                            else -> null
                        }
                    )
                )
            )
        }

        is SyncableMessagePayloadEntity.Location -> {
            val longitudeValue = longitude ?: return null
            val latitudeValue = latitude ?: return null
            NomadDeviceMessageContent(
                content = NomadDeviceMessageContent.Content.Location(
                    NomadDeviceLocation(
                        longitude = longitudeValue,
                        latitude = latitudeValue,
                        name = name,
                        zoom = zoom
                    )
                )
            )
        }

        is SyncableMessagePayloadEntity.Multipart -> NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Multipart(
                NomadDeviceMultipart(
                    text = if (text == null && quotedMessageId == null && mentions.isEmpty()) {
                        null
                    } else {
                        NomadDeviceText(
                            text = text.orEmpty(),
                            mentions = mentions.toNomadDeviceMentions(),
                            quotedMessageId = quotedMessageId
                        )
                    },
                    attachments = attachments.toNomadDeviceAttachments()
                )
            )
        )

        is SyncableMessagePayloadEntity.Unsupported -> null
    }

    private fun MessageReactionsSyncEntity.toReactionJsonElement() = buildJsonObject {
        put(
            "reactions_by_user",
            buildJsonArray {
                reactionsByUser.forEach { reactionByUser ->
                    add(reactionByUser.toJson())
                }
            }
        )
    }

    private fun UserReactionsSyncEntity.toJson() = buildJsonObject {
        put("user_id", userId.toJson())
        put("emojis", buildJsonArray {
            emojis.sorted().forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun MessageReadReceiptsSyncEntity.toReadReceiptJsonElement() = buildJsonObject {
        put("read_receipts", buildJsonArray {
            receipts.forEach { receipt ->
                add(receipt.toJson())
            }
        })
    }

    private fun UserReadReceiptSyncEntity.toJson() = buildJsonObject {
        put("user_id", userId.toJson())
        put("date", date.toString())
    }

    private fun QualifiedIDEntity.toApiConversation(): Conversation = Conversation(id = value, domain = domain)

    private fun QualifiedIDEntity.toJson() = buildJsonObject {
        put("id", value)
        put("domain", domain)
    }

    private fun QualifiedIDEntity.toNomadDeviceQualifiedId(): NomadDeviceQualifiedId =
        NomadDeviceQualifiedId(value = value, domain = domain)

    private fun List<MessageEntity.Mention>.toNomadDeviceMentions(): List<NomadDeviceMention> =
        map { mention ->
            NomadDeviceMention(
                userId = mention.userId.toNomadDeviceQualifiedId(),
                start = mention.start,
                length = mention.length
            )
        }

    @Suppress("CyclomaticComplexMethod")
    private fun List<MessageAttachmentEntity>.toNomadDeviceAttachments(): List<NomadDeviceAttachment> =
        filter { !it.cellAsset }.map { attachment ->
            NomadDeviceAttachment(
                content = NomadDeviceAttachment.Content.Asset(
                    NomadDeviceAsset(
                        mimeType = attachment.mimeType,
                        size = attachment.assetSize ?: 0L,
                        name = attachment.assetPath,
                        otrKey = ByteArr(byteArrayOf()),
                        sha256 = ByteArr(byteArrayOf()),
                        assetId = attachment.assetId,
                        metaData = when {
                            attachment.assetDuration != null && (attachment.assetWidth != null || attachment.assetHeight != null) ->
                                NomadDeviceAsset.MetaData.Video(
                                    NomadDeviceVideoMetaData(
                                        width = attachment.assetWidth,
                                        height = attachment.assetHeight,
                                        durationInMillis = attachment.assetDuration
                                    )
                                )

                            attachment.assetDuration != null ->
                                NomadDeviceAsset.MetaData.Audio(
                                    NomadDeviceAudioMetaData(durationInMillis = attachment.assetDuration)
                                )

                            attachment.assetWidth != null || attachment.assetHeight != null ->
                                NomadDeviceAsset.MetaData.Image(
                                    NomadDeviceImageMetaData(
                                        width = attachment.assetWidth ?: 0,
                                        height = attachment.assetHeight ?: 0
                                    )
                                )

                            attachment.assetPath != null ->
                                NomadDeviceAsset.MetaData.Generic(
                                    NomadDeviceGenericMetaData(name = attachment.assetPath)
                                )

                            else -> null
                        }
                    )
                )
            )
        }
}
