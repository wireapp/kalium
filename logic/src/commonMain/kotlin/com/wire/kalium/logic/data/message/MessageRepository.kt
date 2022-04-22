package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.SendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessagePriority
import com.wire.kalium.network.exceptions.QualifiedSendMessageError
import com.wire.kalium.network.utils.isSuccessful
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map

interface MessageRepository {
    suspend fun getMessagesForConversation(conversationId: ConversationId, limit: Int, offset: Int): Flow<List<Message>>
    suspend fun persistMessage(message: Message): Either<CoreFailure, Unit>
    suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun deleteMessage(messageUuid: String): Either<CoreFailure, Unit>
    suspend fun softDeleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun hideMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun markMessageAsSent(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Unit>
    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message>
    suspend fun getMessagesByConversationAfterDate(conversationId: ConversationId, date: String): Flow<List<Message>>

    // TODO: change the return type to Either<CoreFailure, Unit>
    suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<SendMessageFailure, Unit>
    suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, Unit>
}

class MessageDataSource(
    private val messageApi: MessageApi,
    private val mlsMessageApi: MLSMessageApi,
    private val messageDAO: MessageDAO,
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val sendMessageFailureMapper: SendMessageFailureMapper = MapperProvider.sendMessageFailureMapper()
) : MessageRepository {

    override suspend fun getMessagesForConversation(conversationId: ConversationId, limit: Int, offset: Int): Flow<List<Message>> {
        return messageDAO.getMessagesByConversation(idMapper.toDaoModel(conversationId), limit, offset).map { messageList ->
            messageList.map(messageMapper::fromEntityToMessage)
        }
    }

    override suspend fun persistMessage(message: Message): Either<CoreFailure, Unit> {
        messageDAO.insertMessage(messageMapper.fromMessageToEntity(message))
        //TODO: Handle failures
        return Either.Right(Unit)
    }


    override suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> {
        messageDAO.deleteMessage(messageUuid, idMapper.toDaoModel(conversationId))
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun deleteMessage(messageUuid: String): Either<CoreFailure, Unit> {
        messageDAO.deleteMessage(messageUuid)
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun softDeleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> {
        messageDAO.updateMessageVisibility(
            visibility = MessageEntity.Visibility.DELETED,
            conversationId = idMapper.toDaoModel(conversationId),
            id = messageUuid
        )
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun hideMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> {
        messageDAO.updateMessageVisibility(
            visibility = MessageEntity.Visibility.HIDDEN,
            conversationId = idMapper.toDaoModel(conversationId),
            id = messageUuid
        )
        //TODO: Handle failures
        return Either.Right(Unit)
    }

    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message> =
        wrapStorageRequest {
            messageDAO.getMessageById(messageUuid, idMapper.toDaoModel(conversationId))
                .firstOrNull()?.run {
                    messageMapper.fromEntityToMessage(this)
                }
        }.onSuccess {
            Either.Right(it)
        }.onFailure {
            Either.Left(it)
        }

    override suspend fun getMessagesByConversationAfterDate(conversationId: ConversationId, date: String): Flow<List<Message>> {
        return messageDAO.getMessagesByConversationAfterDate(idMapper.toDaoModel(conversationId), date).map { messageList ->
            messageList.map(messageMapper::fromEntityToMessage)
        }
    }

    override suspend fun markMessageAsSent(conversationId: ConversationId, messageUuid: String) =
        Either.Right(
            messageDAO.updateMessageStatus(MessageEntity.Status.SENT, messageUuid, idMapper.toDaoModel(conversationId))
        )

    override suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<SendMessageFailure, Unit> {
        val recipientMap = envelope.recipients.associate { recipientEntry ->
            idMapper.toApiModel(recipientEntry.userId) to recipientEntry.clientPayloads.associate { clientPayload ->
                clientPayload.clientId.value to clientPayload.payload.data
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
                Either.Left(SendMessageFailure.Unknown(result.kException))
            }
        } else {
            Either.Right(Unit)
        }
    }

    override suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, Unit> =
        wrapApiRequest {
            mlsMessageApi.sendMessage(message)
        }
}
