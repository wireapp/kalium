package com.wire.kalium.persistence.dao.message

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.db.MessagesQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Message as SQLDelightMessage

class MessageMapper {
    fun toModel(msg: SQLDelightMessage): MessageRecord {
        return MessageRecord(
            id = msg.id,
            content = msg.content,
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

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedID) = queries.deleteMessage(id, conversationsId)

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: MessageRecord) =
        queries.insertMessage(
            message.id,
            message.content,
            message.conversationId,
            message.date,
            message.senderUserId,
            message.senderClientId,
            message.status
        )

    override suspend fun insertMessages(messages: List<MessageRecord>) =
        queries.transaction {
            messages.forEach { message ->
                queries.insertMessage(
                    message.id,
                    message.content,
                    message.conversationId,
                    message.date,
                    message.senderUserId,
                    message.senderClientId,
                    message.status
                )
            }
        }

    override suspend fun updateMessage(message: MessageRecord) =
        queries.updateMessages(
            message.content,
            message.date,
            message.senderUserId,
            message.senderClientId,
            message.status,
            message.id,
            message.conversationId
        )

    override suspend fun getAllMessages(): Flow<List<MessageRecord>> =
        queries.selectAllMessages()
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessageById(id: String, conversationId: QualifiedID): Flow<MessageRecord?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { msg -> msg?.let(mapper::toModel) }

    override suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<MessageRecord>> =
        queries.selectByConversationId(conversationId, limit.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
}
