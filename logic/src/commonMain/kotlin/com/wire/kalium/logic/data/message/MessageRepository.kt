package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.persistence.dao.message.Message
import com.wire.kalium.persistence.dao.message.MessageDAO
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    suspend fun getMessagesForConversation(conversationId: QualifiedID, limit: Int): Flow<List<Message>>
    suspend fun persistMessage(message: Message): Either<CoreFailure, Unit>
}

class MessageDataSource(
    private val idMapper: IdMapper,
    private val messageApi: MessageApi,
    private val messageDAO: MessageDAO
) : MessageRepository {

    override suspend fun getMessagesForConversation(conversationId: QualifiedID, limit: Int): Flow<List<Message>> {
        return messageDAO.getMessageByConversation(idMapper.toDaoModel(conversationId), limit)
    }

    override suspend fun persistMessage(message: Message): Either<CoreFailure, Unit> {
        messageDAO.insertMessage(message)
        return Either.Right(Unit)
    }

    //TODO Rework after NetworkResponse in PR #151
    // Either response maybe?
//    suspend fun sendEnvelope(envelope: MessageEnvelope) {
//
//    }

}
