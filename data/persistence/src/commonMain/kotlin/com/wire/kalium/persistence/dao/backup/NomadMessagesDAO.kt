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

import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.WriteDispatcher
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

data class NomadMessageStoreResult(
    val storedMessages: Int,
    val batches: Int,
)

data class NomadMessageToInsert(
    val id: String,
    val conversationId: QualifiedIDEntity,
    val date: Instant,
    val payload: SyncableMessagePayloadEntity,
)

interface NomadMessagesDAO {
    suspend fun storeMessages(
        messages: List<NomadMessageToInsert>,
        batchSize: Int,
    ): NomadMessageStoreResult
}

internal class NomadMessagesDAOImpl internal constructor(
    private val usersQueries: UsersQueries,
    private val conversationsQueries: ConversationsQueries,
    private val messagesQueries: MessagesQueries,
    private val messageAttachmentsQueries: MessageAttachmentsQueries,
    private val writeDispatcher: WriteDispatcher,
) : NomadMessagesDAO {

    override suspend fun storeMessages(
        messages: List<NomadMessageToInsert>,
        batchSize: Int,
    ): NomadMessageStoreResult {
        if (messages.isEmpty()) return NomadMessageStoreResult(storedMessages = 0, batches = 0)

        val normalizedBatchSize = batchSize.coerceAtLeast(MIN_BATCH_SIZE)
        var insertedMessages = 0
        var batches = 0
        withContext(writeDispatcher.value) {
            messages.chunked(normalizedBatchSize).forEach { batch ->
                messagesQueries.transaction {
                    insertPlaceholderUsers(batch)
                    insertPlaceholderConversations(batch)
                    insertedMessages += insertMessages(batch)
                }
                batches += 1
            }
        }

        return NomadMessageStoreResult(
            storedMessages = insertedMessages,
            batches = batches
        )
    }

    private fun insertPlaceholderUsers(messages: List<NomadMessageToInsert>) {
        messages
            .asSequence()
            .map { it.payload.senderUserId }
            .distinct()
            .forEach { usersQueries.insertOrIgnoreUserId(it) }
    }

    private fun insertPlaceholderConversations(messages: List<NomadMessageToInsert>) {
        messages
            .groupBy { it.conversationId }
            .forEach { (conversationId, conversationMessages) ->
                conversationsQueries.insertIncompleteConversationOrBumpLastModifiedDate(
                    qualified_id = conversationId,
                    last_modified_date = conversationMessages.maxOf { it.date },
                )
            }
    }

    private fun insertMessages(messages: List<NomadMessageToInsert>): Int =
        messages.count { message ->
            insertMessageWithContentOrThrow(message)
        }

    private fun insertMessageWithContentOrThrow(message: NomadMessageToInsert): Boolean {
        messagesQueries.insertOrIgnoreMessage(
            id = message.id,
            content_type = message.payload.contentType,
            conversation_id = message.conversationId,
            creation_date = message.date,
            sender_user_id = message.payload.senderUserId,
            sender_client_id = message.payload.senderClientId,
            status = MessageEntity.Status.SENT,
            last_edit_date = message.payload.lastEditDate,
            visibility = MessageEntity.Visibility.VISIBLE,
            expects_read_confirmation = false,
            expire_after_millis = null,
            self_deletion_end_date = null,
        )
        val insertedMessage = messagesQueries.selectChanges().executeAsOne() > 0
        if (!insertedMessage) {
            return false
        }

        val insertedContent = insertRegularContent(message)
        check(insertedContent) {
            "Nomad import inserted base message '${message.id}' without its content row."
        }
        return true
    }

    private fun insertRegularContent(message: NomadMessageToInsert): Boolean {
        val content = message.payload
        return when (content) {
            is SyncableMessagePayloadEntity.Text -> insertTextContent(message, content)
            is SyncableMessagePayloadEntity.Asset -> insertAssetContent(message, content)
            is SyncableMessagePayloadEntity.Location -> insertLocationContent(message, content)
            is SyncableMessagePayloadEntity.Multipart -> insertMultipartContent(message, content)
            is SyncableMessagePayloadEntity.Unsupported -> insertUnknownContent(
                messageId = message.id,
                conversationId = message.conversationId,
                typeName = content.contentType.name
            )
        }
    }

    private fun insertTextContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Text,
    ): Boolean {
        messagesQueries.insertMessageTextContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            text_body = content.text,
            quoted_message_id = content.quotedMessageId,
            is_quote_verified = true,
        )
        val insertedContent = messagesQueries.selectChanges().executeAsOne() > 0
        content.mentions.forEach {
            messagesQueries.insertMessageMention(
                message_id = message.id,
                conversation_id = message.conversationId,
                start = it.start,
                length = it.length,
                user_id = it.userId,
            )
        }
        return insertedContent
    }

    @Suppress("ComplexCondition")
    private fun insertAssetContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Asset,
    ): Boolean {
        if (
            content.size == null ||
            content.mimeType == null ||
            content.otrKey == null ||
            content.sha256 == null ||
            content.assetId == null
        ) {
            return insertUnknownContent(
                messageId = message.id,
                conversationId = message.conversationId,
                typeName = content.contentType.name
            )
        }
        messagesQueries.insertMessageAssetContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            asset_size = content.size,
            asset_name = content.name,
            asset_mime_type = content.mimeType,
            asset_otr_key = content.otrKey,
            asset_sha256 = content.sha256,
            asset_id = content.assetId,
            asset_token = content.assetToken,
            asset_domain = content.assetDomain,
            asset_encryption_algorithm = content.encryptionAlgorithm,
            asset_width = content.width,
            asset_height = content.height,
            asset_duration_ms = content.durationMs,
            asset_normalized_loudness = content.normalizedLoudness,
        )
        return messagesQueries.selectChanges().executeAsOne() > 0
    }

    private fun insertLocationContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Location,
    ): Boolean {
        if (content.latitude == null || content.longitude == null) {
            return insertUnknownContent(
                messageId = message.id,
                conversationId = message.conversationId,
                typeName = content.contentType.name
            )
        }
        messagesQueries.insertLocationMessageContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            latitude = content.latitude,
            longitude = content.longitude,
            name = content.name,
            zoom = content.zoom,
        )
        return messagesQueries.selectChanges().executeAsOne() > 0
    }

    private fun insertMultipartContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Multipart,
    ): Boolean {
        messagesQueries.insertMessageTextContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            text_body = content.text,
            quoted_message_id = content.quotedMessageId,
            is_quote_verified = true,
        )
        val insertedContent = messagesQueries.selectChanges().executeAsOne() > 0
        content.mentions.forEach {
            messagesQueries.insertMessageMention(
                message_id = message.id,
                conversation_id = message.conversationId,
                start = it.start,
                length = it.length,
                user_id = it.userId,
            )
        }
        content.attachments.forEachIndexed { index, attachment ->
            messageAttachmentsQueries.insertCellAttachment(
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_id = attachment.assetId,
                asset_version_id = attachment.assetVersionId,
                cell_asset = true,
                asset_mime_type = attachment.mimeType,
                asset_path = attachment.assetPath,
                asset_size = attachment.assetSize,
                local_path = attachment.localPath ?: "",
                asset_width = attachment.assetWidth,
                asset_height = attachment.assetHeight,
                asset_duration_ms = attachment.assetDuration,
                asset_transfer_status = attachment.assetTransferStatus,
                asset_index = index,
            )
        }
        return insertedContent
    }

    private fun insertUnknownContent(
        messageId: String,
        conversationId: QualifiedIDEntity,
        typeName: String?,
    ): Boolean {
        messagesQueries.insertMessageUnknownContent(
            message_id = messageId,
            conversation_id = conversationId,
            unknown_encoded_data = null,
            unknown_type_name = typeName,
        )
        return messagesQueries.selectChanges().executeAsOne() > 0
    }

    private companion object {
        const val MIN_BATCH_SIZE = 1
    }
}
