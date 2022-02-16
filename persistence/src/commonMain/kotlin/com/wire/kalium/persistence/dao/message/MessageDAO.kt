package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow

data class Message(
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
    suspend fun insertMessage(message: Message)
    suspend fun insertMessages(messages: List<Message>)
    suspend fun updateMessage(message: Message)
    suspend fun getAllMessages(): Flow<List<Message>>
    suspend fun getMessageById(id: String, conversationId: QualifiedID): Flow<Message?>
    suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<Message>>
}
