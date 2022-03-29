package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

sealed class BaseMessageEntity(
    // Not nullable base message fields
    open val id: String,
    open val conversationId: QualifiedIDEntity,
    open val date: String,
    open val senderUserId: QualifiedIDEntity,
    open val senderClientId: String,
    open val status: Status,

    // Text message specific field
    open val content: String? = null,

    // Asset message specific fields
    open val assetMimeType: String? = null,
    open val assetSize: Int? = null,
    open val assetName: String? = null,
    open val assetImageWidth: Int? = null,
    open val assetImageHeight: Int? = null,
    open val assetVideoWidth: Int? = null,
    open val assetVideoHeight: Int? = null,
    open val assetVideoDurationMs: Long? = null,
    open val assetAudioDurationMs: Long? = null,
    open val assetAudioNormalizedLoudness: ByteArray? = null,
    open val assetOtrKey: ByteArray? = null,
    open val assetSha256Key: ByteArray? = null,
    open val assetId: String? = null,
    open val assetToken: String? = null,
    open val assetDomain: String? = null,
    open val assetEncryptionAlgorithm: String? = null,
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    data class TextMessageEntity(
        // Text message specific field
        override val content: String?,

        // BaseMessageEntity fields
        override val id: String,
        override val conversationId: QualifiedIDEntity,
        override val date: String,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String,
        override val status: Status
    ) : BaseMessageEntity(id, conversationId, date, senderUserId, senderClientId, status, content)

    data class AssetMessageEntity(
        // Asset message specific fields
        override val assetMimeType: String? = null,
        override val assetSize: Int? = null,
        override val assetName: String? = null,
        override val assetImageWidth: Int? = null,
        override val assetImageHeight: Int? = null,
        override val assetVideoWidth: Int? = null,
        override val assetVideoHeight: Int? = null,
        override val assetVideoDurationMs: Long? = null,
        override val assetAudioDurationMs: Long? = null,
        override val assetAudioNormalizedLoudness: ByteArray? = null,
        override val assetOtrKey: ByteArray? = null,
        override val assetSha256Key: ByteArray? = null,
        override val assetId: String? = null,
        override val assetToken: String? = null,
        override val assetDomain: String? = null,
        override val assetEncryptionAlgorithm: String? = null,

        // BaseMessageEntity fields
        override val id: String,
        override val conversationId: QualifiedIDEntity,
        override val date: String,
        override val senderUserId: QualifiedIDEntity,
        override val senderClientId: String,
        override val status: Status
    ) : BaseMessageEntity(id, conversationId, date, senderUserId, senderClientId, status)
}

interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: BaseMessageEntity)
    suspend fun insertMessages(messages: List<BaseMessageEntity>)
    suspend fun updateMessage(message: BaseMessageEntity)
    suspend fun getAllMessages(): Flow<List<BaseMessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<BaseMessageEntity?>
    suspend fun getMessageByConversation(conversationId: QualifiedIDEntity, limit: Int): Flow<List<BaseMessageEntity>>
}
