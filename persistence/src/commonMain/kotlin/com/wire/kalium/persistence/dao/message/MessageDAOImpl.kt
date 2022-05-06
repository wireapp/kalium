package com.wire.kalium.persistence.dao.message

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT
import com.wire.kalium.persistence.dao.message.MessageEntity.MessageEntityContent.AssetMessageContent
import com.wire.kalium.persistence.dao.message.MessageEntity.MessageEntityContent.TextMessageContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Message as SQLDelightMessage

class MessageMapper {
    fun toModel(msg: SQLDelightMessage): MessageEntity {

        return MessageEntity(
            content = when (msg.content_type) {
                TEXT -> TextMessageContent(messageBody = msg.text_body ?: "")
                ASSET -> {
                    AssetMessageContent(
                        assetMimeType = msg.asset_mime_type ?: "",
                        assetSizeInBytes = msg.asset_size ?: 0,
                        assetName = msg.asset_name ?: "",
                        assetImageWidth = msg.asset_image_width ?: 0,
                        assetImageHeight = msg.asset_image_height ?: 0,
                        assetOtrKey = msg.asset_otr_key ?: ByteArray(16),
                        assetSha256Key = msg.asset_sha256 ?: ByteArray(16),
                        assetId = msg.asset_id ?: "",
                        assetToken = msg.asset_token ?: "",
                        assetDomain = msg.asset_domain ?: "",
                        assetEncryptionAlgorithm = msg.asset_encryption_algorithm ?: "",
                    )
                }
            },

            // Common Message fields
            id = msg.id,
            conversationId = msg.conversation_id,
            date = msg.date,
            senderUserId = msg.sender_user_id,
            senderClientId = msg.sender_client_id,
            status = msg.status,
            visibility = msg.visibility
        )
    }
}

class MessageDAOImpl(private val queries: MessagesQueries) : MessageDAO {
    private val mapper = MessageMapper()

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = queries.deleteMessage(id, conversationsId)

    override suspend fun deleteMessage(id: String) = queries.deleteMessageById(id)

    override suspend fun markMessageAsDeleted(id: String) =
        queries.markMessageAsDeleted(id)

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: MessageEntity) = insertInDB(message)

    override suspend fun insertMessages(messages: List<MessageEntity>) =
        queries.transaction {
            messages.forEach { insertInDB(it) }
        }

    private fun insertInDB(message: MessageEntity) {
        queries.insertMessage(
            id = message.id,
            text_body = when (message.content) {
                is TextMessageContent -> message.content.messageBody
                else -> null
            },
            content_type = contentTypeOf(message.content),
            asset_mime_type = if (message.content is AssetMessageContent) message.content.assetMimeType else null,
            asset_size = if (message.content is AssetMessageContent) message.content.assetSizeInBytes else null,
            asset_name = if (message.content is AssetMessageContent) message.content.assetName else null,
            asset_image_width = if (message.content is AssetMessageContent) message.content.assetImageWidth else null,
            asset_image_height = if (message.content is AssetMessageContent) message.content.assetImageHeight else null,
            asset_otr_key = if (message.content is AssetMessageContent) message.content.assetOtrKey else null,
            asset_sha256 = if (message.content is AssetMessageContent) message.content.assetSha256Key else null,
            asset_id = if (message.content is AssetMessageContent) message.content.assetId else null,
            asset_token = if (message.content is AssetMessageContent) message.content.assetToken else null,
            asset_domain = if (message.content is AssetMessageContent) message.content.assetDomain else null,
            asset_encryption_algorithm = if (message.content is AssetMessageContent) message.content.assetEncryptionAlgorithm else null,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = message.senderClientId,
            visibility = message.visibility,
            status = message.status
        )
    }

    override suspend fun updateMessage(message: MessageEntity) =
        queries.updateMessages(
            id = message.id,
            text_body = when (message.content) {
                is TextMessageContent -> message.content.messageBody
                else -> null
            },
            content_type = contentTypeOf(message.content),
            asset_mime_type = if (message.content is AssetMessageContent) message.content.assetMimeType else null,
            asset_size = if (message.content is AssetMessageContent) message.content.assetSizeInBytes else null,
            asset_name = if (message.content is AssetMessageContent) message.content.assetMimeType else null,
            asset_image_width = if (message.content is AssetMessageContent) message.content.assetImageWidth else null,
            asset_image_height = if (message.content is AssetMessageContent) message.content.assetImageHeight else null,
            asset_otr_key = if (message.content is AssetMessageContent) message.content.assetOtrKey else null,
            asset_sha256 = if (message.content is AssetMessageContent) message.content.assetSha256Key else null,
            asset_id = if (message.content is AssetMessageContent) message.content.assetId else null,
            asset_token = if (message.content is AssetMessageContent) message.content.assetToken else null,
            asset_domain = if (message.content is AssetMessageContent) message.content.assetDomain else null,
            asset_encryption_algorithm = if (message.content is AssetMessageContent) message.content.assetEncryptionAlgorithm else null,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = message.senderClientId,
            visibility = message.visibility,
            status = message.status
        )

    private fun contentTypeOf(content: MessageEntity.MessageEntityContent): MessageEntity.ContentType = when (content) {
        is TextMessageContent -> TEXT
        is AssetMessageContent -> ASSET
    }

    override suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageStatus(status, id, conversationId)

    override suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageDate(date, id, conversationId)

    override suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status) =
        queries.updateMessagesAddMillisToDate(millis, conversationId, status)

    override suspend fun getMessagesFromAllConversations(limit: Int, offset: Int): Flow<List<MessageEntity>> =
        queries.selectAllMessages(limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { msg -> msg?.let(mapper::toModel) }

    override suspend fun getMessagesByConversation(conversationId: QualifiedIDEntity, limit: Int, offset: Int): Flow<List<MessageEntity>> =
        queries.selectByConversationId(conversationId, limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessagesByConversationAfterDate(conversationId: QualifiedIDEntity, date: String): Flow<List<MessageEntity>> =
        queries.selectMessagesByConversationIdAfterDate(conversationId, date)
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity> {
        return queries.selectMessagesFromUserByStatus(userId, MessageEntity.Status.PENDING)
            .executeAsList()
            .map(mapper::toModel)
    }
}
