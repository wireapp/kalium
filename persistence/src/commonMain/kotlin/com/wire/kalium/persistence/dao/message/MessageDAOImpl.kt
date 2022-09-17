package com.wire.kalium.persistence.dao.message

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrDefault
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.FAILED_DECRYPTION
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.KNOCK
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.MEMBER_CHANGE
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.MISSED_CALL
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.RESTRICTED_ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.UNKNOWN
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Message as SQLDelightMessage

class MessageDAOImpl(private val queries: MessagesQueries) : MessageDAO {
    private val mapper = MessageMapper(queries)

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = queries.deleteMessage(id, conversationsId)

    override suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity) =
        queries.markMessageAsDeleted(id, conversationsId)

    override suspend fun markAsEdited(editTimeStamp: String, conversationId: QualifiedIDEntity, id: String) {
        queries.markMessageAsEdited(editTimeStamp, id, conversationId)
    }

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: MessageEntity) = insertInDB(message)

    override suspend fun insertMessages(messages: List<MessageEntity>) =
        queries.transaction {
            messages.forEach { insertInDB(it) }
        }

    @Suppress("ComplexMethod", "LongMethod")
    private fun insertInDB(message: MessageEntity) {
        queries.insertMessage(
            id = message.id,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = if (message is MessageEntity.Regular) message.senderClientId else null,
            visibility = message.visibility,
            status = message.status,
            content_type = contentTypeOf(message.content)
        )
        when (val content = message.content) {
            is MessageEntityContent.Text -> {
                queries.insertMessageTextContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    text_body = content.messageBody
                )
                content.mentions.forEach {
                    queries.insertMessageMention(
                        message_id = message.id,
                        conversation_id = message.conversationId,
                        start = it.start,
                        length = it.length,
                        user_id = it.userId
                    )
                }
            }

            is MessageEntityContent.RestrictedAsset -> queries.insertMessageRestrictedAssetContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_mime_type = content.mimeType,
                asset_size = content.assetSizeInBytes,
                asset_name = content.assetName
            )

            is MessageEntityContent.Asset -> queries.insertMessageAssetContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_size = content.assetSizeInBytes,
                asset_name = content.assetName,
                asset_mime_type = content.assetMimeType,
                asset_download_status = content.assetDownloadStatus,
                asset_otr_key = content.assetOtrKey,
                asset_sha256 = content.assetSha256Key,
                asset_id = content.assetId,
                asset_token = content.assetToken,
                asset_domain = content.assetDomain,
                asset_encryption_algorithm = content.assetEncryptionAlgorithm,
                asset_width = content.assetWidth,
                asset_height = content.assetHeight,
                asset_duration_ms = content.assetDurationMs,
                asset_normalized_loudness = content.assetNormalizedLoudness
            )

            is MessageEntityContent.Unknown -> queries.insertMessageUnknownContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
                unknown_type_name = content.typeName
            )

            is MessageEntityContent.FailedDecryption -> queries.insertFailedDecryptionMessageContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
            )

            is MessageEntityContent.MemberChange -> queries.insertMemberChangeMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                member_change_list = content.memberUserIdList,
                member_change_type = content.memberChangeType
            )

            is MessageEntityContent.MissedCall -> queries.insertMissedCallMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                caller_id = message.senderUserId
            )

            is MessageEntityContent.Knock -> {
                /** NO-OP. No need to insert any content for Knock messages */
            }

        }
    }

    override suspend fun updateAssetDownloadStatus(
        downloadStatus: MessageEntity.DownloadStatus,
        id: String,
        conversationId: QualifiedIDEntity
    ) = queries.updateAssetDownloadStatus(downloadStatus, id, conversationId)

    override suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageStatus(status, id, conversationId)

    override suspend fun updateMessageId(conversationId: QualifiedIDEntity, oldMessageId: String, newMessageId: String) {
        queries.updateMessageId(newMessageId, oldMessageId, conversationId)
    }

    override suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageDate(date, id, conversationId)

    override suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status) =
        queries.updateMessagesAddMillisToDate(millis, conversationId, status)

    override suspend fun getMessagesFromAllConversations(limit: Int, offset: Int): Flow<List<MessageEntity>> =
        queries.selectAllMessages(limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .flatMapLatest { it?.let { mapper.toMessageEntityFlow(it) } ?: flowOf(null) }

    override suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>> =
        queries.selectByConversationIdAndVisibility(conversationId, visibility, limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    override suspend fun getMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>> =
        queries.selectMessagesByConversationIdAndVisibilityAfterDate(conversationId, visibility, date)
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    override suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity> =
        queries.selectMessagesFromUserByStatus(userId, MessageEntity.Status.PENDING)
            .executeAsList()
            .map(mapper::toMessageEntity)

    override suspend fun updateTextMessageContent(
        conversationId: QualifiedIDEntity,
        messageId: String,
        newTextContent: MessageEntityContent.Text
    ) {
        queries.transaction {
            queries.updateMessageTextContent(newTextContent.messageBody, messageId, conversationId)
            queries.deleteMessageMentions(messageId, conversationId)
            newTextContent.mentions.forEach {
                queries.insertMessageMention(
                    message_id = messageId,
                    conversation_id = conversationId,
                    start = it.start,
                    length = it.length,
                    user_id = it.userId
                )
            }
        }
    }

    override suspend fun getConversationMessagesByContentType(
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType
    ): List<MessageEntity> =
        queries.getConversationMessagesByContentType(conversationId, contentType)
            .executeAsList()
            .map { mapper.toMessageEntity(it) }

    override suspend fun deleteAllConversationMessages(conversationId: QualifiedIDEntity) {
        queries.deleteAllConversationMessages(conversationId)
    }

    override suspend fun observeLastUnreadMessage(
        conversationID: QualifiedIDEntity
    ): Flow<MessageEntity?> = queries.getLastUnreadMessage(conversationID).asFlow().mapToOneOrNull().map {
        it?.let {
            mapper.toMessageEntity(it)
        }
    }.distinctUntilChanged()

    override suspend fun observeUnreadMessageCount(conversationId: QualifiedIDEntity): Flow<Long> =
        queries.getUnreadMessageCount(conversationId).asFlow().mapToOneOrDefault(0L)
            .distinctUntilChanged()

    override suspend fun observeUnreadMentionsCount(conversationId: QualifiedIDEntity, userId: UserIDEntity): Flow<Long> =
        queries.getUnreadMentionsCount(conversationId, userId).asFlow().mapToOneOrDefault(0L)
            .distinctUntilChanged()

    private fun contentTypeOf(content: MessageEntityContent): MessageEntity.ContentType = when (content) {
        is MessageEntityContent.Text -> TEXT
        is MessageEntityContent.Asset -> ASSET
        is MessageEntityContent.Knock -> KNOCK
        is MessageEntityContent.MemberChange -> MEMBER_CHANGE
        is MessageEntityContent.MissedCall -> MISSED_CALL
        is MessageEntityContent.Unknown -> UNKNOWN
        is MessageEntityContent.FailedDecryption -> FAILED_DECRYPTION
        is MessageEntityContent.RestrictedAsset -> RESTRICTED_ASSET
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<SQLDelightMessage>>.toMessageEntityListFlow(): Flow<List<MessageEntity>> = this.flatMapLatest {
        if (it.isEmpty()) flowOf(listOf())
        else combine(it.map { message -> mapper.toMessageEntityFlow(message) }) { it.asList() }
    }

    override val platformExtensions: MessageExtensions = MessageExtensionsImpl(queries, mapper)
}
