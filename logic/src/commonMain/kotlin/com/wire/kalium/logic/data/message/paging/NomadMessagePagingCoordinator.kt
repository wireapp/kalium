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

package com.wire.kalium.logic.data.message.paging

import co.touchlab.stately.collections.ConcurrentMutableMap
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.network.api.authenticated.nomaddevice.Conversation
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreRequest
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadBatchRestoreResponse
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadConversationBatchRestore
import com.wire.kalium.network.api.authenticated.nomaddevice.NomadStoredMessage
import com.wire.kalium.network.api.base.authenticated.nomaddevice.NomadDeviceSyncApi
import com.wire.kalium.nomaddevice.NomadMappedMessages
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.backup.NomadMessageToInsert
import com.wire.kalium.persistence.dao.backup.NomadMessagesDAO
import com.wire.kalium.persistence.dao.backup.SyncableMessagePayloadEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.attachment.MessageAttachmentEntity
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAsset
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceAttachment
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessageContent
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceMessagePayload
import com.wire.kalium.protobuf.nomaddevice.NomadDeviceQualifiedId
import io.mockative.Mockable
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.io.encoding.Base64

@Mockable
internal interface NomadMessagePagingCoordinator {
    suspend fun fetchOlderMessagesIfNeeded(
        conversationId: ConversationId,
        pageSize: Int,
        beforeTimestampMs: Long?,
        onInvalidate: () -> Unit
    )
}

internal class NomadMessagePagingCoordinatorImpl(
    private val selfUserId: UserId,
    private val isNomadEnabled: () -> Boolean,
    private val nomadDeviceSyncApi: NomadDeviceSyncApi,
    private val nomadMessagesDAO: NomadMessagesDAO,
    private val mapper: NomadBatchRestoreMapper = NomadBatchRestoreMapper(),
    private val clock: Clock = Clock.System,
) : NomadMessagePagingCoordinator {

    private data class State(
        val nextCursor: Long = 0L,
        val nextTimestamp: Long,
        val hasMore: Boolean = true,
        val isFetching: Boolean = false,
    )

    private val stateByConversation = ConcurrentMutableMap<ConversationId, State>()

    override suspend fun fetchOlderMessagesIfNeeded(
        conversationId: ConversationId,
        pageSize: Int,
        beforeTimestampMs: Long?,
        onInvalidate: () -> Unit
    ) {
        kaliumLogger.d("[$TAG] Nomad paging boundary reached for conversation '${conversationId.toLogString()}'")

        val currentState = stateByConversation.block { map ->
            val existing = map[conversationId] ?: State(
                nextTimestamp = beforeTimestampMs ?: clock.now().toEpochMilliseconds()
            )
            if (!existing.hasMore || existing.isFetching) return@block null
            if (!isNomadEnabled()) {
                map[conversationId] = existing.copy(hasMore = false)
                kaliumLogger.d("[$TAG] Nomad paging disabled for conversation '${conversationId.toLogString()}'")
                return@block null
            }

            val next = existing.copy(isFetching = true)
            map[conversationId] = next
            kaliumLogger.d(
                "[$TAG] Nomad paging fetching conversation '${conversationId.toLogString()}' with cursor=${next.nextCursor} ts=${next.nextTimestamp}"
            )
            next
        } ?: return

        val responseResult = wrapApiRequest {
            nomadDeviceSyncApi.restoreMessagesBatch(
                NomadBatchRestoreRequest(
                    conversationIds = listOf(conversationId.value),
                    limit = pageSize,
                    beforeTimestamp = currentState.nextTimestamp,
                    nextCursor = currentState.nextCursor,
                )
            )
        }

        when (responseResult) {
            is Either.Left -> {
                stateByConversation.block { map ->
                    val state = map[conversationId] ?: currentState
                    map.put(conversationId, state.copy(isFetching = false))
                }
                kaliumLogger.w(
                    "[$TAG] Nomad batch restore failed for '${selfUserId.toLogString()}': ${responseResult.value}"
                )
            }

            is Either.Right -> storeAndUpdateState(
                conversationId = conversationId,
                response = responseResult.value,
                currentState = currentState,
                pageSize = pageSize,
                onInvalidate = onInvalidate,
            )
        }
    }

    private suspend fun storeAndUpdateState(
        conversationId: ConversationId,
        response: NomadBatchRestoreResponse,
        currentState: State,
        pageSize: Int,
        onInvalidate: () -> Unit,
    ) {
        val mapped = mapper.map(response)
        val storeResult = wrapStorageRequest {
            nomadMessagesDAO.storeMessages(
                messages = mapped.messages.filterByConversationId(conversationId),
                batchSize = pageSize,
            )
        }

        var shouldInvalidate = false
        stateByConversation.block { map ->
            val state = map[conversationId] ?: currentState
            when (storeResult) {
                is Either.Left -> {
                    map[conversationId] = state.copy(isFetching = false)
                    kaliumLogger.w(
                        "[$TAG] Nomad batch restore storage failed for '${selfUserId.toLogString()}': ${storeResult.value}"
                    )
                }

                is Either.Right -> {
                    val nextState = response.nextStateFor(conversationId, state)
                    map[conversationId] = nextState
                    kaliumLogger.d(
                        "[$TAG] Nomad paging stored ${storeResult.value.storedMessages} messages for conversation " +
                            "'${conversationId.toLogString()}', hasMore=${nextState.hasMore}, " +
                            "nextCursor=${nextState.nextCursor}, nextTimestamp=${nextState.nextTimestamp}"
                    )
                    if (storeResult.value.storedMessages > 0) {
                        shouldInvalidate = true
                    }
                }
            }
        }
        if (shouldInvalidate) {
            onInvalidate()
        }
    }

    internal class NomadBatchRestoreMapper {
        fun map(response: NomadBatchRestoreResponse): NomadMappedMessages {
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
                skippedMessages = skipped,
            )
        }

        @Suppress("ReturnCount")
        private fun mapMessageOrNull(
            conversationWithMessages: NomadConversationBatchRestore,
            storedMessage: NomadStoredMessage,
        ): NomadMessageToInsert? {
            val payloadBytes = runCatching { Base64.Default.decode(storedMessage.payload) }.getOrElse {
                logSkip(storedMessage, conversationWithMessages, "invalid base64 payload")
                return null
            }

            val payload = runCatching {
                NomadDeviceMessagePayload.decodeFromByteArray(payloadBytes)
            }.getOrElse {
                logSkip(storedMessage, conversationWithMessages, "invalid protobuf payload")
                return null
            }

            val senderUserId = payload.senderUserId.toDaoQualifiedId()
            val creationDate = payload.creationDate.let { Instant.fromEpochMilliseconds(it) }
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
                date = Instant.fromEpochMilliseconds(storedMessage.timestamp),
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
                is NomadDeviceMessageContent.Content.Text ->
                    SyncableMessagePayloadEntity.Text(
                        creationDate = creationDate,
                        senderUserId = senderUserId,
                        senderClientId = senderClientId,
                        lastEditDate = lastEditDate,
                        text = contentValue.value.text,
                        mentions = contentValue.value.mentions.map { mention ->
                            MessageEntity.Mention(
                                start = mention.start,
                                length = mention.length,
                                userId = mention.userId.toDaoQualifiedId(),
                            )
                        },
                        quotedMessageId = contentValue.value.quotedMessageId,
                    )

                is NomadDeviceMessageContent.Content.Asset ->
                    contentValue.value.toSyncableAssetContent(
                        senderUserId = senderUserId,
                        senderClientId = senderClientId,
                        creationDate = creationDate,
                        lastEditDate = lastEditDate,
                    )

                is NomadDeviceMessageContent.Content.Location ->
                    SyncableMessagePayloadEntity.Location(
                        creationDate = creationDate,
                        senderUserId = senderUserId,
                        senderClientId = senderClientId,
                        lastEditDate = lastEditDate,
                        latitude = contentValue.value.latitude,
                        longitude = contentValue.value.longitude,
                        name = contentValue.value.name,
                        zoom = contentValue.value.zoom,
                    )

                is NomadDeviceMessageContent.Content.Multipart ->
                    SyncableMessagePayloadEntity.Multipart(
                        creationDate = creationDate,
                        senderUserId = senderUserId,
                        senderClientId = senderClientId,
                        lastEditDate = lastEditDate,
                        text = contentValue.value.text?.text,
                        mentions = contentValue.value.text?.mentions.orEmpty().map { mention ->
                            MessageEntity.Mention(
                                start = mention.start,
                                length = mention.length,
                                userId = mention.userId.toDaoQualifiedId(),
                            )
                        },
                        quotedMessageId = contentValue.value.text?.quotedMessageId,
                        attachments = contentValue.value.attachments.mapNotNull { attachment ->
                            attachment.toMessageAttachmentOrNull()
                        },
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

        private fun NomadDeviceAttachment.toMessageAttachmentOrNull():
                MessageAttachmentEntity? {
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
                assetTransferStatus = com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity.NOT_DOWNLOADED.name,
                contentUrl = null,
                contentHash = null,
                assetIndex = null,
                contentExpiresAt = null,
                isEditSupported = false,
            )
        }

        private fun NomadDeviceAsset.MetaData<*>?.widthOrNull(): Int? =
            when (this) {
                is NomadDeviceAsset.MetaData.Image -> value.width
                is NomadDeviceAsset.MetaData.Video -> value.width
                else -> null
            }

        private fun NomadDeviceAsset.MetaData<*>?.heightOrNull(): Int? =
            when (this) {
                is NomadDeviceAsset.MetaData.Image -> value.height
                is NomadDeviceAsset.MetaData.Video -> value.height
                else -> null
            }

        private fun NomadDeviceAsset.MetaData<*>?.durationOrNull(): Long? =
            when (this) {
                is NomadDeviceAsset.MetaData.Video -> value.durationInMillis
                is NomadDeviceAsset.MetaData.Audio -> value.durationInMillis
                else -> null
            }

        private fun NomadDeviceAsset.MetaData<*>?.normalizedLoudnessOrNull(): ByteArray? =
            when (this) {
                is NomadDeviceAsset.MetaData.Audio -> value.normalization?.array
                else -> null
            }

        private fun logSkip(
            storedMessage: NomadStoredMessage,
            conversation: NomadConversationBatchRestore,
            reason: String,
        ) {
            kaliumLogger.w(
                "[$TAG] Skipping Nomad message '${storedMessage.messageId}' in conversation " +
                        "'${conversation.conversation.id}@${conversation.conversation.domain}': $reason."
            )
        }

        private fun Conversation.toDaoConversationId():
                QualifiedIDEntity =
            QualifiedIDEntity(value = id, domain = domain)

        private fun NomadDeviceQualifiedId.toDaoQualifiedId():
                QualifiedIDEntity =
            QualifiedIDEntity(value = value, domain = domain)
    }

    private fun NomadBatchRestoreResponse.nextStateFor(
        conversationId: ConversationId,
        current: State,
    ): State {
        val entry = conversations.firstOrNull {
            it.conversation.id == conversationId.value && it.conversation.domain == conversationId.domain
        }
        if (entry == null) {
            return current.copy(isFetching = false, hasMore = false)
        }

        return current.copy(
            nextCursor = entry.nextCursor,
            nextTimestamp = entry.nextTimestamp,
            hasMore = entry.hasMore,
            isFetching = false,
        )
    }

    private fun List<NomadMessageToInsert>.filterByConversationId(
        conversationId: ConversationId
    ): List<NomadMessageToInsert> = filter { message ->
        message.conversationId.value == conversationId.value && message.conversationId.domain == conversationId.domain
    }

    companion object {
        const val TAG = "NomadMessagePagingCoordinator"
    }
}
