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

import app.cash.sqldelight.async.coroutines.awaitAsOne
import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import com.wire.kalium.persistence.CellFilesQueries
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessageAttachmentsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.ReceiptsQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.UsersQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.db.WriteDispatcher
import com.wire.kalium.util.time.UNIX_FIRST_DATE
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant

data class NomadMessageStoreResult(
    val storedMessages: Int,
    val batches: Int,
)

data class NomadReactionToInsert(
    val userId: QualifiedIDEntity,
    val emojis: List<String>,
)

data class NomadReadReceiptToInsert(
    val userId: QualifiedIDEntity,
    val date: Instant,
)

data class NomadMessageToInsert(
    val id: String,
    val conversationId: QualifiedIDEntity,
    val date: Instant,
    val payload: SyncableMessagePayloadEntity,
    val reactions: List<NomadReactionToInsert> = emptyList(),
    val readReceipts: List<NomadReadReceiptToInsert> = emptyList(),
)

interface NomadMessagesDAO {
    suspend fun storeMessages(
        messages: List<NomadMessageToInsert>,
        batchSize: Int,
    ): NomadMessageStoreResult
}

@Suppress("LongParameterList")
internal class NomadMessagesDAOImpl internal constructor(
    private val usersQueries: UsersQueries,
    private val conversationsQueries: ConversationsQueries,
    private val messagesQueries: MessagesQueries,
    private val cellFilesQueries: CellFilesQueries,
    private val messageAttachmentsQueries: MessageAttachmentsQueries,
    private val reactionsQueries: ReactionsQueries,
    private val receiptsQueries: ReceiptsQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val selfUserId: UserIDEntity,
    private val writeDispatcher: WriteDispatcher,
) : NomadMessagesDAO {

    private val contentWriter = NomadMessageContentWriter(
        messagesQueries = messagesQueries,
        cellFilesQueries = cellFilesQueries,
        messageAttachmentsQueries = messageAttachmentsQueries,
    )

    private val unreadEventWriter = NomadUnreadEventWriter(
        messagesQueries = messagesQueries,
        unreadEventsQueries = unreadEventsQueries,
        selfUserId = selfUserId,
    )

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
                    val lastReadDatesByConversation = batch.lastReadDatesByConversation(conversationsQueries)
                    insertedMessages += insertMessages(batch, lastReadDatesByConversation)
                }
                batches += 1
            }
        }

        return NomadMessageStoreResult(
            storedMessages = insertedMessages,
            batches = batches
        )
    }

    private suspend fun insertPlaceholderUsers(messages: List<NomadMessageToInsert>) {
        messages
            .asSequence()
            .map { it.payload.senderUserId }
            .distinct()
            .forEach { usersQueries.insertOrIgnoreUserId(it) }
    }

    private suspend fun insertPlaceholderConversations(messages: List<NomadMessageToInsert>) {
        messages
            .groupBy { it.conversationId }
            .forEach { (conversationId, conversationMessages) ->
                conversationsQueries.insertIncompleteConversationOrBumpLastModifiedDate(
                    qualified_id = conversationId,
                    last_modified_date = conversationMessages.maxOf { it.date },
                )
            }
    }

    private suspend fun insertMessages(
        messages: List<NomadMessageToInsert>,
        lastReadDatesByConversation: Map<QualifiedIDEntity, Instant>,
    ): Int =
        messages.count { message ->
            insertMessageWithContentOrThrow(message, lastReadDatesByConversation)
        }

    private suspend fun insertMessageWithContentOrThrow(
        message: NomadMessageToInsert,
        lastReadDatesByConversation: Map<QualifiedIDEntity, Instant>,
    ): Boolean {
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
        val insertedMessage = messagesQueries.selectChanges().awaitAsOne() > 0
        if (!insertedMessage) {
            return false
        }

        val insertedContent = contentWriter.insertRegularContent(message)
        check(insertedContent) {
            "Nomad import inserted base message '${message.id}' without its content row."
        }
        insertReactions(message)
        insertReadReceipts(message)
        unreadEventWriter.insertUnreadEvent(message, lastReadDatesByConversation)
        return true
    }

    private suspend fun insertReactions(message: NomadMessageToInsert) {
        message.reactions.forEach { reaction ->
            reaction.emojis.forEach { emoji ->
                reactionsQueries.insertReaction(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    sender_id = reaction.userId,
                    emoji = emoji,
                    date = message.date.toString()
                )
            }
        }
    }

    private suspend fun insertReadReceipts(message: NomadMessageToInsert) {
        message.readReceipts.forEach { receipt ->
            receiptsQueries.insertReceipt(
                message.id,
                "${message.conversationId.value}@${message.conversationId.domain}",
                "${receipt.userId.value}@${receipt.userId.domain}",
                RECEIPT_TYPE_READ,
                receipt.date.toString()
            )
        }
    }

    private companion object {
        const val MIN_BATCH_SIZE = 1
        const val RECEIPT_TYPE_READ = "READ"
    }
}

private suspend fun List<NomadMessageToInsert>.lastReadDatesByConversation(
    conversationsQueries: ConversationsQueries,
): Map<QualifiedIDEntity, Instant> {
    val conversations = asSequence()
        .map { it.conversationId }
        .distinct()
        .toList()

    return conversations.associateWith { conversationId ->
        conversationsQueries.getConversationLastReadDate(conversationId).awaitAsOneOrNull()
                ?: Instant.DISTANT_PAST
    }
}

private class NomadUnreadEventWriter(
    private val messagesQueries: MessagesQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val selfUserId: UserIDEntity,
) {

    suspend fun insertUnreadEvent(
        message: NomadMessageToInsert,
        lastReadDatesByConversation: Map<QualifiedIDEntity, Instant>,
    ) {
        val lastRead = lastReadDatesByConversation[message.conversationId]
        val olderMessageDate = lastRead == null || lastRead <= Instant.UNIX_FIRST_DATE || message.date <= lastRead
        if (message.payload.senderUserId == selfUserId || olderMessageDate) {
            return
        }

        when (val content = message.payload) {
            is SyncableMessagePayloadEntity.Text -> insertUnreadTextLikeContent(
                message = message,
                quotedMessageId = content.quotedMessageId,
                mentions = content.mentions
            )

            is SyncableMessagePayloadEntity.Multipart -> insertUnreadTextLikeContent(
                message = message,
                quotedMessageId = content.quotedMessageId,
                mentions = content.mentions
            )

            is SyncableMessagePayloadEntity.Asset,
            is SyncableMessagePayloadEntity.Location -> {
                unreadEventsQueries.insertEvent(
                    message.id,
                    UnreadEventTypeEntity.MESSAGE,
                    message.conversationId,
                    message.date
                )
            }

            is SyncableMessagePayloadEntity.Unsupported -> {
                /* no-op */
            }
        }
    }

    private suspend fun insertUnreadTextLikeContent(
        message: NomadMessageToInsert,
        quotedMessageId: String?,
        mentions: List<MessageEntity.Mention>,
    ) {
        val isQuotingSelfUser = quotedMessageId?.let { quotedId ->
            messagesQueries.getMessageSenderId(quotedId, message.conversationId).awaitAsOneOrNull() == selfUserId
        } ?: false

        val unreadType = when {
            isQuotingSelfUser -> UnreadEventTypeEntity.REPLY
            mentions.any { it.userId == selfUserId } -> UnreadEventTypeEntity.MENTION
            else -> UnreadEventTypeEntity.MESSAGE
        }

        unreadEventsQueries.insertEvent(
            message.id,
            unreadType,
            message.conversationId,
            message.date
        )
    }
}

private class NomadMessageContentWriter(
    private val messagesQueries: MessagesQueries,
    private val cellFilesQueries: CellFilesQueries,
    private val messageAttachmentsQueries: MessageAttachmentsQueries,
) {

    suspend fun insertRegularContent(message: NomadMessageToInsert): Boolean {
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

    private suspend fun insertTextContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Text,
    ): Boolean {
        messagesQueries.insertMessageTextContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            text_body = content.text,
            quoted_message_id = content.quotedMessageId,
            quoted_message_conversation_id = null,
            is_quote_verified = true,
        )
        val insertedContent = messagesQueries.selectChanges().awaitAsOne() > 0
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
    private suspend fun insertAssetContent(
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
        return messagesQueries.selectChanges().awaitAsOne() > 0
    }

    private suspend fun insertLocationContent(
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
        return messagesQueries.selectChanges().awaitAsOne() > 0
    }

    private suspend fun insertMultipartContent(
        message: NomadMessageToInsert,
        content: SyncableMessagePayloadEntity.Multipart,
    ): Boolean {
        messagesQueries.insertMessageTextContent(
            message_id = message.id,
            conversation_id = message.conversationId,
            text_body = content.text,
            quoted_message_id = content.quotedMessageId,
            quoted_message_conversation_id = null,
            is_quote_verified = true,
        )
        val insertedContent = messagesQueries.selectChanges().awaitAsOne() > 0
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
            cellFilesQueries.upsertAttachmentFile(
                uuid = attachment.assetId,
                conversationId = message.conversationId.toString(),
                localPath = attachment.localPath,
                size = attachment.assetSize,
                downloadedAt = message.date.toEpochMilliseconds(),
                modifiedAt = message.date.toEpochMilliseconds(),
                isOffline = 0,
                assetVersionId = attachment.assetVersionId,
                cellAsset = 1,
                contentUrl = attachment.contentUrl,
                previewUrl = attachment.previewUrl,
                assetMimeType = attachment.mimeType,
                assetPath = attachment.assetPath,
                contentHash = attachment.contentHash,
                assetWidth = attachment.assetWidth?.toLong(),
                assetHeight = attachment.assetHeight?.toLong(),
                assetDurationMs = attachment.assetDuration,
                assetTransferStatus = attachment.assetTransferStatus,
                contentUrlExpiresAt = attachment.contentExpiresAt,
                editSupported = if (attachment.isEditSupported) 1 else 0,
            )
            messageAttachmentsQueries.insertCellAttachment(
                asset_id = attachment.assetId,
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_index = index.toLong(),
            )
        }
        return insertedContent
    }

    private suspend fun insertUnknownContent(
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
        return messagesQueries.selectChanges().awaitAsOne() > 0
    }
}
