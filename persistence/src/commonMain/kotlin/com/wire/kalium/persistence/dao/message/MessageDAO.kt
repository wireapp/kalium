package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
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
    fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    fun deleteMessage(id: String)
    fun updateMessageVisibility(visibility: MessageEntity.Visibility, id: String, conversationId: QualifiedIDEntity)
    fun deleteAllMessages()
    fun insertMessage(message: MessageEntity)
    fun insertMessages(messages: List<MessageEntity>)
    fun updateMessage(message: MessageEntity)
    fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    fun getAllMessagesFlow(): Flow<List<MessageEntity>>
    fun getMessageById(id: String, conversationId: QualifiedIDEntity): MessageEntity?
    fun getMessageByConversationFlow(conversationId: QualifiedIDEntity, limit: Int): Flow<List<MessageEntity>>
}
