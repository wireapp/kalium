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

import com.wire.kalium.network.api.authenticated.nomaddevice.LastRead
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncEvent
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAudioMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceGenericMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceImageMetaData
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceLocation
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMultipart
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceText
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceVideoMetaData
import pbandk.ByteArr
import pbandk.encodeToByteArray
import kotlin.io.encoding.Base64

internal class NomadRemoteBackupChangeLogEventMapper {
    fun mapBatchToApiEvents(batch: ChangeLogSyncBatch): List<NomadMessageEvent> {
        val events = batch.events.mapNotNull { it.toApiEventOrNull() }.toMutableList()
        val lastReads = batch.conversationLastReads.map {
            LastRead(
                conversationId = it.conversationId.toString(),
                lastReadTimestamp = it.lastReadDate.epochSeconds
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
            reaction = reactions.toReactionPayload()
        )

        is ChangeLogSyncEvent.ReadReceiptSync -> NomadMessageEvent.UpsertMessageStatusEvent(
            messageId = messageId,
            conversation = conversationId.toApiConversation(),
            readReceipt = readReceipts.toReadReceiptsPayload()
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

    private fun SyncableMessagePayloadEntity.toNomadDeviceContentOrNull(): NomadDeviceMessageContent? = when (this) {
        is SyncableMessagePayloadEntity.Text -> toNomadDeviceTextContent()
        is SyncableMessagePayloadEntity.Asset -> toNomadDeviceAssetContentOrNull()
        is SyncableMessagePayloadEntity.Location -> toNomadDeviceLocationContentOrNull()
        is SyncableMessagePayloadEntity.Multipart -> toNomadDeviceMultipartContent()
        is SyncableMessagePayloadEntity.Unsupported -> null
    }

    private fun SyncableMessagePayloadEntity.Text.toNomadDeviceTextContent(): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Text(
                NomadDeviceText(
                    text = text.orEmpty(),
                    mentions = mentions.toNomadDeviceMentions(),
                    quotedMessageId = quotedMessageId
                )
            )
        )

    private fun SyncableMessagePayloadEntity.Asset.toNomadDeviceAssetContentOrNull(): NomadDeviceMessageContent? {

        @Suppress("ComplexCondition")
        if (mimeType == null || size == null || otrKey == null || sha256 == null || assetId == null) return null

        return NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Asset(
                NomadDeviceAsset(
                    mimeType = mimeType!!,
                    size = size!!,
                    name = name,
                    otrKey = ByteArr(otrKey!!),
                    sha256 = ByteArr(sha256!!),
                    assetId = assetId!!,
                    assetToken = assetToken,
                    assetDomain = assetDomain,
                    encryption = encryptionAlgorithm,
                    metaData = toNomadDeviceAssetMetaData()
                )
            )
        )
    }

    private fun SyncableMessagePayloadEntity.Asset.toNomadDeviceAssetMetaData(): NomadDeviceAsset.MetaData<*>? = when {
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

    private fun SyncableMessagePayloadEntity.Location.toNomadDeviceLocationContentOrNull(): NomadDeviceMessageContent? {
        if (longitude == null || latitude == null) return null

        return NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Location(
                NomadDeviceLocation(
                    longitude = longitude!!,
                    latitude = latitude!!,
                    name = name,
                    zoom = zoom
                )
            )
        )
    }

    private fun SyncableMessagePayloadEntity.Multipart.toNomadDeviceMultipartContent(): NomadDeviceMessageContent =
        NomadDeviceMessageContent(
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

}
