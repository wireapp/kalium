package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.message.MessageApi
import com.wire.kalium.network.api.message.MessagePriority
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
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
    suspend fun markMessageAsDeleted(messageUuid: String, conversationId: ConversationId): Either<StorageFailure, Unit>
    suspend fun updateMessageStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun updateMessageDate(conversationId: ConversationId, messageUuid: String, date: String): Either<CoreFailure, Unit>
    suspend fun updatePendingMessagesAddMillisToDate(conversationId: ConversationId, millis: Long): Either<CoreFailure, Unit>
    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message>
    suspend fun getMessagesByConversationAfterDate(conversationId: ConversationId, date: String): Flow<List<Message>>

    /**
     * Send a Proteus [MessageEnvelope] to the given [conversationId].
     *
     * @return [Either.Right] with the server date time in case of success
     * @return [Either.Left] of a [ProteusSendMessageFailure] if the server rejected the message
     * @return [Either.Left] of other [CoreFailure] for more generic cases
     */
    suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<CoreFailure, String>
    suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, Unit>

    suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>>
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

    override suspend fun markMessageAsDeleted(messageUuid: String, conversationId: ConversationId): Either<StorageFailure, Unit> = wrapStorageRequest {
        messageDAO.markMessageAsDeleted(id = messageUuid)
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

    override suspend fun updateMessageStatus(messageStatus: MessageEntity.Status, conversationId: ConversationId, messageUuid: String) =
        wrapStorageRequest {
            messageDAO.updateMessageStatus(messageStatus, messageUuid, idMapper.toDaoModel(conversationId))
        }

    override suspend fun updateMessageDate(conversationId: ConversationId, messageUuid: String, date: String) =
        wrapStorageRequest {
            messageDAO.updateMessageDate(date, messageUuid, idMapper.toDaoModel(conversationId))
        }

    override suspend fun updatePendingMessagesAddMillisToDate(conversationId: ConversationId, millis: Long) =
        wrapStorageRequest {
            messageDAO.updateMessagesAddMillisToDate(millis, idMapper.toDaoModel(conversationId), MessageEntity.Status.PENDING)
        }

    override suspend fun sendEnvelope(conversationId: ConversationId, envelope: MessageEnvelope): Either<CoreFailure, String> {
        val recipientMap = envelope.recipients.associate { recipientEntry ->
            idMapper.toApiModel(recipientEntry.userId) to recipientEntry.clientPayloads.associate { clientPayload ->
                clientPayload.clientId.value to clientPayload.payload.data
            }
        }
        return wrapApiRequest {
            messageApi.qualifiedSendMessage(
                //TODO Handle other MessageOptions, native push, transient and priorities
                MessageApi.Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap, true, MessagePriority.HIGH, false, null, MessageApi.QualifiedMessageOption.ReportAll
                ),
                idMapper.toApiModel(conversationId),
            )
        }.fold({ networkFailure ->
            val failure = when {
                networkFailure is NetworkFailure.ServerMiscommunication
                        && networkFailure.rootCause is ProteusClientsChangedError -> {
                    sendMessageFailureMapper.fromDTO(networkFailure.rootCause as ProteusClientsChangedError)
                }
                else -> networkFailure
            }
            Either.Left(failure)
        }, {
            Either.Right(it.time)
        })
    }

    override suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, Unit> =
        wrapApiRequest {
            mlsMessageApi.sendMessage(message)
        }

    override suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>> = wrapStorageRequest {
        messageDAO.getAllPendingMessagesFromUser(idMapper.toDaoModel(senderUserId))
            .map(messageMapper::fromEntityToMessage)
    }
}
