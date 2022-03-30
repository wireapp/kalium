package com.wire.kalium.persistence.dao.message

import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.db.MessagesQueries
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.db.Message as SQLDelightMessage

class MessageMapper {
    fun toModel(msg: SQLDelightMessage): MessageEntity {
        return MessageEntity(
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

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = queries.deleteMessage(id, conversationsId)

    override suspend fun deleteMessage(id: String) = queries.deleteMessageById(id)

    override suspend fun updateMessageVisibility(deleteStatus: MessageEntity.Visibility, content: String?, id: String) =
        queries.updateMessageVisibility(deleteStatus, content, id)

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: MessageEntity) =
        queries.insertMessage(
            message.id,
            message.content,
            message.conversationId,
            message.date,
            message.senderUserId,
            message.senderClientId,
            message.status,
            message.visibility
        )

    override suspend fun insertMessages(messages: List<MessageEntity>) =
        queries.transaction {
            messages.forEach { message ->
                queries.insertMessage(
                    message.id,
                    message.content,
                    message.conversationId,
                    message.date,
                    message.senderUserId,
                    message.senderClientId,
                    message.status,
                    message.visibility
                )
            }
        }

    override suspend fun updateMessage(message: MessageEntity) =
        queries.updateMessages(
            message.content,
            message.date,
            message.senderUserId,
            message.senderClientId,
            message.status,
            message.id,
            message.conversationId
        )

    override suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageStatus(status, id, conversationId)

    override suspend fun getAllMessages(): Flow<List<MessageEntity>> =
        queries.selectAllMessages()
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }

    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .map { msg -> msg?.let(mapper::toModel) }

    override suspend fun getMessageByConversation(conversationId: QualifiedIDEntity, limit: Int): Flow<List<MessageEntity>> =
        queries.selectByConversationId(conversationId, limit.toLong())
            .asFlow()
            .mapToList()
            .map { entryList -> entryList.map(mapper::toModel) }
}
