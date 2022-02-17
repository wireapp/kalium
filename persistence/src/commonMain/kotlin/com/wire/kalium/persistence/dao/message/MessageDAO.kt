package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.QualifiedID
import kotlinx.coroutines.flow.Flow

data class MessageRecord(
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
    suspend fun insertMessage(message: MessageRecord)
    suspend fun insertMessages(messages: List<MessageRecord>)
    suspend fun updateMessage(message: MessageRecord)
    suspend fun getAllMessages(): Flow<List<MessageRecord>>
    suspend fun getMessageById(id: String, conversationId: QualifiedID): Flow<MessageRecord?>
    suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<MessageRecord>>
}
