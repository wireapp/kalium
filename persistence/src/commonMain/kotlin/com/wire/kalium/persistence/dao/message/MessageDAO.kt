package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow

data class MessageEntity(
    val id: String,
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
            val assetSizeInBytes: Long,
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
    suspend fun markMessageAsDeleted(id: String /*todo: add conversation id since the message id is not unique*/)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status)
    suspend fun getMessagesFromAllConversations(limit: Int, offset: Int): Flow<List<MessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?>
    suspend fun getMessagesByConversation(conversationId: QualifiedIDEntity, limit: Int, offset: Int): Flow<List<MessageEntity>>
    suspend fun getMessagesByConversationAfterDate(conversationId: QualifiedIDEntity, date: String): Flow<List<MessageEntity>>
    suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity>
}
