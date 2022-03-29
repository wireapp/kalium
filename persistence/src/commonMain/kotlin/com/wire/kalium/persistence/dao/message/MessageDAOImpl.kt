package com.wire.kalium.persistence.dao.message

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.message.BaseMessageEntity.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.BaseMessageEntity.TextMessageEntity
import com.wire.kalium.persistence.db.MessagesQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Message as SQLDelightMessage

class MessageMapper {
    fun toModel(msg: SQLDelightMessage): BaseMessageEntity {

        return when {
            msg.content == null && msg.asset_mime_type != null -> AssetMessageEntity(
                // Asset Message fields
                assetMimeType = msg.asset_mime_type,

                // Common Message fields
                id = msg.id,
                conversationId = msg.conversation_id,
                date = msg.date,
                senderUserId = msg.sender_user_id,
                senderClientId = msg.sender_client_id,
                status = msg.status
            )
            else -> TextMessageEntity(
                content = msg.content,
                id = msg.id,
                conversationId = msg.conversation_id,
                date = msg.date,
                senderUserId = msg.sender_user_id,
                senderClientId = msg.sender_client_id,
                status = msg.status
            )
        }
    }

    fun toAssetModel(msg: SQLDelightMessage): BaseMessageEntity {
        return AssetMessageEntity(
            id = msg.id,
            conversationId = msg.conversation_id,
            date = msg.date,
            senderUserId = msg.sender_user_id,
            senderClientId = msg.sender_client_id,
            status = msg.status
        )
    }
}

class MessageDAOImpl(private val queries: MessagesQueries) : MessageDAO {
    private val mapper = MessageMapper()

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = queries.deleteMessage(id, conversationsId)

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: BaseMessageEntity) =
        queries.insertMessage(
            id = message.id,
            content = message.content,
            asset_mime_type = message.assetMimeType,
            asset_size = message.assetSize,
            asset_name = message.assetName,
            asset_image_width = message.assetImageWidth,
            asset_image_height = message.assetImageHeight,
            asset_otr_key = message.assetOtrKey,
            asset_sha256 = message.assetSha256Key,
            asset_id = message.assetId,
            asset_token = message.assetToken,
            asset_domain = message.assetDomain,
            asset_encryption_algorithm = message.assetEncryptionAlgorithm,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = message.senderClientId,
            status = message.status
        )

    override suspend fun insertMessages(messages: List<BaseMessageEntity>) =
        queries.transaction {
            messages.forEach { message ->
                queries.insertMessage(
                    id = message.id,
                    content = message.content,
                    asset_mime_type = message.assetMimeType,
                    asset_size = message.assetSize,
                    asset_name = message.assetName,
                    asset_image_width = message.assetImageWidth,
                    asset_image_height = message.assetImageHeight,
                    asset_otr_key = message.assetOtrKey,
                    asset_sha256 = message.assetSha256Key,
                    asset_id = message.assetId,
                    asset_token = message.assetToken,
                    asset_domain = message.assetDomain,
                    asset_encryption_algorithm = message.assetEncryptionAlgorithm,
                    conversation_id = message.conversationId,
                    date = message.date,
                    sender_user_id = message.senderUserId,
                    sender_client_id = message.senderClientId,
                    status = message.status
                )
            }
        }

    override suspend fun updateMessage(message: BaseMessageEntity) =
        queries.updateMessages(
            id = message.id,
            content = message.content,
            asset_mime_type = message.assetMimeType,
            asset_size = message.assetSize,
            asset_name = message.assetName,
            asset_image_width = message.assetImageWidth,
            asset_image_height = message.assetImageHeight,
            asset_otr_key = message.assetOtrKey,
            asset_sha256 = message.assetSha256Key,
            asset_id = message.assetId,
            asset_token = message.assetToken,
            asset_domain = message.assetDomain,
            asset_encryption_algorithm = message.assetEncryptionAlgorithm,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = message.senderClientId,
            status = message.status
        )

    override suspend fun getAllMessages(): Flow<List<BaseMessageEntity>> =
        queries.selectAllMessages()
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<BaseMessageEntity?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { msg -> msg?.let(mapper::toModel) }

    override suspend fun getMessageByConversation(conversationId: QualifiedIDEntity, limit: Int): Flow<List<BaseMessageEntity>> =
        queries.selectByConversationId(conversationId, limit.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
}
