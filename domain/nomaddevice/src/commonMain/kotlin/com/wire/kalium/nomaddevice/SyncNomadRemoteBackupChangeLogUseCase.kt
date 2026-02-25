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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.LastRead
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEvent
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadMessageEventsRequest
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncBatch
import com.wire.kalium.persistence.dao.backup.ChangeLogSyncEvent
import com.wire.kalium.persistence.dao.backup.RemoteBackupChangeLogDAO
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.receipt.MessageReadReceiptsSyncEntity
import com.wire.kalium.persistence.dao.receipt.UserReadReceiptSyncEntity
import com.wire.kalium.persistence.dao.reaction.MessageReactionsSyncEntity
import com.wire.kalium.persistence.dao.reaction.UserReactionsSyncEntity
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
import com.wire.kalium.userstorage.di.UserStorageProvider
import kotlin.io.encoding.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import pbandk.ByteArr
import pbandk.encodeToByteArray

public data class NomadRemoteBackupChangeLogSyncResult(
    val syncedEntries: Int,
    val postedEvents: Int,
)

/**
 * Use case that reads a remote-backup changelog page from DB, maps it to Nomad API events,
 * posts it to Nomad, and removes that page from changelog only after a successful post.
 */
public class SyncNomadRemoteBackupChangeLogUseCase internal constructor(
    private val repository: NomadRemoteBackupChangeLogSyncRepository,
    private val pageSize: Long = DEFAULT_PAGE_SIZE,
) {

    internal constructor(
        remoteBackupChangeLogDAOProvider: (UserId) -> RemoteBackupChangeLogDAO?,
        nomadDeviceSyncApiProvider: (UserId) -> NomadDeviceSyncApi,
        pageSize: Long = DEFAULT_PAGE_SIZE,
    ) : this(
        repository = NomadRemoteBackupChangeLogSyncDataSource(
            remoteBackupChangeLogDAOProvider = remoteBackupChangeLogDAOProvider,
            nomadDeviceSyncApiProvider = nomadDeviceSyncApiProvider
        ),
        pageSize = pageSize
    )

    public constructor(
        userStorageProvider: UserStorageProvider,
        nomadAuthenticatedNetworkAccess: NomadAuthenticatedNetworkAccess,
        pageSize: Long = DEFAULT_PAGE_SIZE,
    ) : this(
        repository = NomadRemoteBackupChangeLogSyncDataSource(
            remoteBackupChangeLogDAOProvider = { userId ->
                userStorageProvider.get(userId)?.database?.remoteBackupChangeLogDAO
            },
            nomadDeviceSyncApiProvider = { userId ->
                nomadAuthenticatedNetworkAccess.nomadDeviceSyncApi(userId.toNetworkUserId())
            }
        ),
        pageSize = pageSize
    )

    public suspend operator fun invoke(selfUserId: UserId): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> {
        val batch = when (val result = repository.getLastPendingChangesBatch(selfUserId, pageSize)) {
            is Either.Left -> return result.value.left()
            is Either.Right -> result.value
        }

        if (batch.events.isEmpty()) {
            return NomadRemoteBackupChangeLogSyncResult(syncedEntries = 0, postedEvents = 0).right()
        }

        val mappedEvents = mapBatchToApiEvents(batch)
        if (mappedEvents.isNotEmpty()) {
            val request = NomadMessageEventsRequest(events = mappedEvents)
            when (val result = repository.postMessageEvents(selfUserId, request)) {
                is Either.Left -> return result.value.left()
                is Either.Right -> Unit
            }
        } else {
            nomadLogger.w(
                "Dropping ${batch.events.size} changelog entries that cannot be mapped to Nomad events " +
                    "for '${selfUserId.toLogString()}'."
            )
        }

        val changesToDelete = batch.events.map { it.change }
        when (val result = repository.deleteChanges(selfUserId, changesToDelete)) {
            is Either.Left -> return result.value.left()
            is Either.Right -> Unit
        }

        return NomadRemoteBackupChangeLogSyncResult(
            syncedEntries = changesToDelete.size,
            postedEvents = mappedEvents.size
        ).right()
    }

    @Deprecated(
        message = "Use invoke(selfUserId) on SyncNomadRemoteBackupChangeLogUseCase",
        replaceWith = ReplaceWith("invoke(selfUserId)")
    )
    public suspend fun syncNextPage(selfUserId: UserId): Either<CoreFailure, NomadRemoteBackupChangeLogSyncResult> =
        invoke(selfUserId)

    private fun mapBatchToApiEvents(batch: ChangeLogSyncBatch): List<NomadMessageEvent> {
        val events = batch.events.mapNotNull { it.toApiEventOrNull() }.toMutableList()
        val lastReads = batch.conversationLastReads.map {
            LastRead(
                conversationId = it.conversationId.toString(),
                lastRead = it.lastReadDate.toString()
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

    private fun SyncableMessagePayloadEntity.toNomadDeviceContentOrNull(): NomadDeviceMessageContent? = when (this) {
        is SyncableMessagePayloadEntity.Text -> NomadDeviceMessageContent(
            content = NomadDeviceMessageContent.Content.Text(
                NomadDeviceText(
                    text = text.orEmpty(),
                    mentions = mentionsJson.parseMentionsFromJson(),
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
                    text = if (text == null && quotedMessageId == null && mentionsJson.parseMentionsFromJson().isEmpty()) {
                        null
                    } else {
                        NomadDeviceText(
                            text = text.orEmpty(),
                            mentions = mentionsJson.parseMentionsFromJson(),
                            quotedMessageId = quotedMessageId
                        )
                    },
                    attachments = attachmentsJson.parseAttachmentsFromJson()
                )
            )
        )

        is SyncableMessagePayloadEntity.Unsupported -> null
    }

    private fun MessageReactionsSyncEntity.toReactionJsonElement() = buildJsonObject {
        put("reactions_by_user", buildJsonArray {
            reactionsByUser.forEach { reactionByUser ->
                add(reactionByUser.toJson())
            }
        })
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

    private fun String.parseMentionsFromJson(): List<NomadDeviceMention> {
        val parsed = runCatching { jsonParser.parseToJsonElement(this).jsonArray }.getOrNull() ?: return emptyList()
        return parsed.mapNotNull { mentionElement ->
            val mentionObject = mentionElement as? JsonObject ?: return@mapNotNull null
            val start = mentionObject.intOrNull("start") ?: return@mapNotNull null
            val length = mentionObject.intOrNull("length") ?: return@mapNotNull null
            val userObject = mentionObject["userId"] as? JsonObject ?: return@mapNotNull null
            val userValue = userObject.stringOrNull("value") ?: return@mapNotNull null
            val userDomain = userObject.stringOrNull("domain") ?: return@mapNotNull null
            NomadDeviceMention(
                userId = NomadDeviceQualifiedId(value = userValue, domain = userDomain),
                start = start,
                length = length
            )
        }
    }

    private fun String.parseAttachmentsFromJson(): List<NomadDeviceAttachment> {
        val parsed = runCatching { jsonParser.parseToJsonElement(this).jsonArray }.getOrNull() ?: return emptyList()
        return parsed.mapNotNull { attachmentElement ->
            val attachmentObject = attachmentElement as? JsonObject ?: return@mapNotNull null
            val isCellAttachment = attachmentObject.booleanLikeOrNull("cell_asset") ?: true
            if (isCellAttachment) return@mapNotNull null

            val assetId = attachmentObject.stringOrNull("id") ?: return@mapNotNull null
            val mimeType = attachmentObject.stringOrNull("mime_type") ?: return@mapNotNull null
            val size = attachmentObject.longOrNull("asset_size") ?: 0L
            val width = attachmentObject.intOrNull("asset_width")
            val height = attachmentObject.intOrNull("asset_height")
            val duration = attachmentObject.longOrNull("asset_duration_ms")
            val name = attachmentObject.stringOrNull("asset_path")

            NomadDeviceAttachment(
                content = NomadDeviceAttachment.Content.Asset(
                    NomadDeviceAsset(
                        mimeType = mimeType,
                        size = size,
                        name = name,
                        otrKey = ByteArr(byteArrayOf()),
                        sha256 = ByteArr(byteArrayOf()),
                        assetId = assetId,
                        metaData = when {
                            duration != null && (width != null || height != null) ->
                                NomadDeviceAsset.MetaData.Video(
                                    NomadDeviceVideoMetaData(
                                        width = width,
                                        height = height,
                                        durationInMillis = duration
                                    )
                                )

                            duration != null ->
                                NomadDeviceAsset.MetaData.Audio(
                                    NomadDeviceAudioMetaData(durationInMillis = duration)
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
    }

    private fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotEmpty() }

    private fun JsonObject.intOrNull(key: String): Int? {
        val primitive = (this[key] as? JsonPrimitive) ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val primitive = (this[key] as? JsonPrimitive) ?: return null
        return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull()
    }

    private fun JsonObject.booleanLikeOrNull(key: String): Boolean? {
        val primitive = (this[key] as? JsonPrimitive) ?: return null
        primitive.booleanOrNull?.let { return it }
        return when (primitive.contentOrNull?.lowercase()) {
            "1", "true" -> true
            "0", "false" -> false
            else -> null
        }
    }

    private companion object {
        const val DEFAULT_PAGE_SIZE: Long = 100

        val jsonParser = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }
}

@Deprecated(
    message = "Use SyncNomadRemoteBackupChangeLogUseCase",
    replaceWith = ReplaceWith("SyncNomadRemoteBackupChangeLogUseCase")
)
public typealias NomadRemoteBackupChangeLogSyncer = SyncNomadRemoteBackupChangeLogUseCase

private fun UserId.toNetworkUserId(): com.wire.kalium.network.api.model.UserId =
    com.wire.kalium.network.api.model.QualifiedID(value = value, domain = domain)
