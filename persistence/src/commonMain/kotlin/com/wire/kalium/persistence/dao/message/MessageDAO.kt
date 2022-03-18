package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow

data class MessageEntity(
    val id: String,
    val content: String?,
    val conversationId: QualifiedID,
    val date: String,
    val senderUserId: QualifiedID,
    val senderClientId: String,
    val status: Status
) {
    enum class Status {
        PENDING, SENT, READ, FAILED
    }
}

interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedID)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: MessageEntity)
    suspend fun insertMessages(messages: List<MessageEntity>)
    suspend fun updateMessage(message: MessageEntity)
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedID)
    suspend fun getAllMessages(): Flow<List<MessageEntity>>
    suspend fun getMessageById(id: String, conversationId: QualifiedID): Flow<MessageEntity?>
    suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<MessageEntity>>
}
