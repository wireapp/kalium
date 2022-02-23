package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.persistence.dao.message.MessageDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MessageRepository {
    suspend fun getMessagesForConversation(conversationId: ConversationId, limit: Int): Flow<List<Message>>
    suspend fun persistMessage(message: Message): Either<CoreFailure, Unit>
}

class MessageDataSource(
    private val messageApi: MessageApi,
    private val messageDAO: MessageDAO,
    private val messageMapper: MessageMapper,
    private val idMapper: IdMapper
) : MessageRepository {

    override suspend fun getMessagesForConversation(conversationId: ConversationId, limit: Int): Flow<List<Message>> {
        return messageDAO.getMessageByConversation(idMapper.toDaoModel(conversationId), limit).map { messageList ->
            messageList.map(messageMapper::fromEntityToMessage)
        }
    }

    override suspend fun persistMessage(message: Message): Either<CoreFailure, Unit> {
        messageDAO.insertMessage(messageMapper.fromMessageToEntity(message))
        //TODO: Handle failures
        return Either.Right(Unit)
    }

}
