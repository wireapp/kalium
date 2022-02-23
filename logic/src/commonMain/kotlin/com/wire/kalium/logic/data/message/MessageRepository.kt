package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.util.Base64
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessagePriority
import com.wire.kalium.network.exceptions.QualifiedSendMessageError
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.message.MessageDAO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

interface MessageRepository {
    suspend fun getMessagesForConversation(conversationId: ConversationId, limit: Int): Flow<List<Message>>
    suspend fun persistMessage(message: Message): Either<CoreFailure, Unit>
    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message>
    suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<CoreFailure, Unit>
}

class MessageDataSource(
    private val messageApi: MessageApi,
    private val messageDAO: MessageDAO,
    private val messageMapper: MessageMapper,
    private val idMapper: IdMapper,
    private val sendMessageFailureMapper: SendMessageFailureMapper
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

    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message> {
        //TODO handle failures
        return Either.Right(
            messageDAO.getMessageById(messageUuid, idMapper.toDaoModel(conversationId))
                .filterNotNull()
                .map(messageMapper::fromEntityToMessage)
                .first()
        )
    }

    override suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<CoreFailure, Unit> {
        val recipientMap = envelope.recipients.associate { recipientEntry ->
            idMapper.toApiModel(recipientEntry.userId) to recipientEntry.clientPayloads.associate { clientPayload ->
                clientPayload.clientId.value to Base64.encodeToBase64(clientPayload.payload.data).decodeToString()
            }
        }
        val result = messageApi.qualifiedSendMessage(
            //TODO Handle other MessageOptions, native push, transient and priorities
            MessageApi.Parameters.QualifiedDefaultParameters(
                envelope.senderClientId.value,
                recipientMap, true, MessagePriority.HIGH, false, null, MessageApi.QualifiedMessageOption.ReportAll
            ),
            idMapper.toApiModel(conversationId),
        )
        return if (!result.isSuccessful()) {
            val exception = result.kException
            if (exception is QualifiedSendMessageError.MissingDeviceError) {
                Either.Left(sendMessageFailureMapper.fromDTO(exception))
            } else {
                //TODO handle different cases
                Either.Left(CoreFailure.Unknown(result.kException))
            }
        } else {
            Either.Right(Unit)
        }
    }
}
