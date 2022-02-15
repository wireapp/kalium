package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow

data class Message(
    val id: QualifiedID,
    val content: String?,
    val conversationId: QualifiedID,
    val timestamp: Long,
    val senderId: QualifiedID,
    val status: String
)

interface MessageDAO {
    suspend fun deleteMessage(id: QualifiedID)
    suspend fun deleteAllMessages()
    suspend fun insertMessage(message: Message)
    suspend fun insertMessages(messages: List<Message>)
    suspend fun updateMessage(message: Message)
    suspend fun getAllMessages(): Flow<List<Message>>
    suspend fun getMessageById(id: QualifiedID): Flow<Message?>
    suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<Message>>
}
