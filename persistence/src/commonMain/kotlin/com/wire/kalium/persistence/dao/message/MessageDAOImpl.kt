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
    fun toModel(msg: SQLDelightMessage): Message {
        return Message(
            id = msg.qualified_id,
            content = msg.content,
            conversationId = msg.conversation_id!!,
            timestamp = msg.timestamp!!,
            senderId = msg.sender_id!!,
            status = msg.status!!
        )
    }
}

class MessageDAOImpl(private val queries: MessagesQueries) : MessageDAO {
    private val mapper = MessageMapper()

    override suspend fun deleteMessage(id: QualifiedID) = queries.deleteMessage(id)

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: Message) =
        queries.insertMessage(message.id, message.content, message.conversationId, message.timestamp, message.senderId, message.status)

    override suspend fun insertMessages(messages: List<Message>) =
        queries.transaction {
            messages.forEach { message ->
                queries.insertMessage(
                    message.id,
                    message.content,
                    message.conversationId,
                    message.timestamp,
                    message.senderId,
                    message.status
                )
            }
        }

    override suspend fun updateMessage(message: Message) =
        queries.updateMessages(message.content, message.conversationId, message.timestamp, message.senderId, message.status, message.id)

    override suspend fun getAllMessages(): Flow<List<Message>> =
        queries.selectAllMessages()
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessageById(id: QualifiedID): Flow<Message?> =
        queries.selectByQualifiedId(id)
            .asFlow()
            .mapToOneOrNull()
            .map { msg -> msg?.let(mapper::toModel) }

    override suspend fun getMessageByConversation(conversationId: QualifiedID, limit: Int): Flow<List<Message>> =
        queries.selectByConversationId(conversationId, limit.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
}
