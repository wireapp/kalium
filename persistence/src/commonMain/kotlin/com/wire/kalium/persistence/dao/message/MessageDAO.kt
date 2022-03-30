package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow

data class MessageEntity(
    val id: String,
    val content: String?,
    val conversationId: QualifiedIDEntity,
    val date: String,
    val senderUserId: QualifiedIDEntity,
    val senderClientId: String,
    val status: Status,
    val visibility: Visibility = Visibility.VISIBLE
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }

    enum class Visibility {
        VISIBLE, DELETED, HIDDEN
    }
}

interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun deleteMessage(id: String)
    suspend fun updateMessageVisibility(visibility: MessageEntity.Visibility, content: String? = null, id: String)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun getAllMessages(): Flow<List<MessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?>
    suspend fun getMessageByConversation(conversationId: QualifiedIDEntity, limit: Int): Flow<List<MessageEntity>>
}
