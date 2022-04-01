package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

data class MessageEntity(
    val id: String,
    val contentType: ContentType,
    val content: MessageEntityContent,
    val conversationId: QualifiedIDEntity,
    val date: String,
    val senderUserId: QualifiedIDEntity,
    val senderClientId: String,
    val status: Status,
    val visibility: Visibility = Visibility.VISIBLE
) {
    sealed class MessageEntityContent {
        data class TextMessageContent(val messageBody: String) : MessageEntityContent()
        data class AssetMessageContent(
            val assetMimeType: String,
            val assetSize: Int,
            val assetName: String? = null,
            val assetImageWidth: Int? = null,
            val assetImageHeight: Int? = null,
            val assetVideoWidth: Int? = null,
            val assetVideoHeight: Int? = null,
            val assetVideoDurationMs: Long? = null,
            val assetAudioDurationMs: Long? = null,
            val assetAudioNormalizedLoudness: ByteArray? = null,
            val assetOtrKey: ByteArray,
            val assetSha256Key: ByteArray,
            val assetId: String,
            val assetToken: String? = null,
            val assetDomain: String? = null,
            val assetEncryptionAlgorithm: String?,
        ) : MessageEntityContent()
    }

    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    enum class ContentType {
        TEXT, ASSET
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}

interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun deleteMessage(id: String)
    suspend fun updateMessageVisibility(visibility: MessageEntity.Visibility, id: String, conversationId: QualifiedIDEntity)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun getAllMessages(): Flow<List<MessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?>
    suspend fun getMessageByConversation(conversationId: QualifiedIDEntity, limit: Int): Flow<List<MessageEntity>>
}
