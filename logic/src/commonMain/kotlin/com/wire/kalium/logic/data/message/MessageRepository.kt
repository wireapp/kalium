/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.notification.LocalNotificationConversation
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.feature.message.MessageTarget
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest

@Suppress("TooManyFunctions")
interface MessageRepository {
    /**
     * this fun should never be used directly, use PersistMessageUseCase() instead
     * @see PersistMessageUseCase
     */
    @DelicateKaliumApi(
        message = "Calling this function directly may cause conversation list to be displayed in an incorrect order",
        replaceWith = ReplaceWith("com.wire.kalium.logic.data.message.PersistMessageUseCase")
    )
    suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationReadDate: Boolean = false,
        updateConversationModifiedDate: Boolean = false,
    ): Either<CoreFailure, Unit>

    suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun markMessageAsDeleted(messageUuid: String, conversationId: ConversationId): Either<StorageFailure, Unit>
    suspend fun updateMessageStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun updateAssetMessageUploadStatus(
        uploadStatus: Message.UploadStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun updateAssetMessageDownloadStatus(
        downloadStatus: Message.DownloadStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun updateMessageDate(conversationId: ConversationId, messageUuid: String, date: String): Either<CoreFailure, Unit>
    suspend fun updatePendingMessagesAddMillisToDate(conversationId: ConversationId, millis: Long): Either<CoreFailure, Unit>
    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message>
    suspend fun getMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList()
    ): Flow<List<Message>>

    suspend fun getNotificationMessage(messageSizePerConversation: Int = 10): Either<CoreFailure, Flow<List<LocalNotificationConversation>>>

    suspend fun getMessagesByConversationIdAndVisibilityAfterDate(
        conversationId: ConversationId,
        date: String,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList()
    ): Flow<List<Message>>

    /**
     * Send a Proteus [MessageEnvelope] to the given [conversationId].
     *
     * @return [Either.Right] with the server date time in case of success
     * @return [Either.Left] of a [ProteusSendMessageFailure] if the server rejected the message
     * @return [Either.Left] of other [CoreFailure] for more generic cases
     */
    suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget
    ): Either<CoreFailure, String>

    suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, String>

    suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>>
    suspend fun getPendingConfirmationMessagesByConversationAfterDate(
        conversationId: ConversationId,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList()
    ): Either<CoreFailure, List<String>>

    suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editTimeStamp: String
    ): Either<CoreFailure, Unit>

    suspend fun resetAssetProgressStatus()
    suspend fun markMessagesAsDecryptionResolved(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
    ): Either<CoreFailure, Unit>

    suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ReceiptMode?>

    val extensions: MessageRepositoryExtensions
}

// TODO: suppress TooManyFunctions for now, something we need to fix in the future
@Suppress("LongParameterList", "TooManyFunctions")
class MessageDataSource(
    private val messageApi: MessageApi,
    private val mlsMessageApi: MLSMessageApi,
    private val messageDAO: MessageDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val sendMessageFailureMapper: SendMessageFailureMapper = MapperProvider.sendMessageFailureMapper(),
    private val selfUserId: UserId,
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper()
) : MessageRepository {

    override val extensions: MessageRepositoryExtensions = MessageRepositoryExtensionsImpl(messageDAO, messageMapper)

    override suspend fun getMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
        visibility: List<Message.Visibility>
    ): Flow<List<Message>> =
        messageDAO.getMessagesByConversationAndVisibility(
            conversationId.toDao(),
            limit,
            offset,
            visibility.map { it.toEntityVisibility() }
        ).map { messagelist -> messagelist.map(messageMapper::fromEntityToMessage) }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getNotificationMessage(
        messageSizePerConversation: Int
    ): Either<CoreFailure, Flow<List<LocalNotificationConversation>>> = wrapStorageRequest {
        messageDAO.getNotificationMessage(
            listOf(
                MessageEntity.ContentType.TEXT,
                MessageEntity.ContentType.RESTRICTED_ASSET,
                MessageEntity.ContentType.ASSET,
                MessageEntity.ContentType.KNOCK,
                MessageEntity.ContentType.MISSED_CALL
            )
        ).mapLatest {
            it.groupBy { item ->
                item.conversationId
            }.map { (conversationId, messages) ->
                LocalNotificationConversation(
                    // todo: needs some clean up!
                    id = conversationId.toModel(),
                    conversationName = messages.first().conversationName ?: "",
                    messages = messages.take(messageSizePerConversation)
                        .map { message -> messageMapper.fromMessageToLocalNotificationMessage(message) },
                    isOneToOneConversation = messages.first().conversationType?.let { type ->
                        type == ConversationEntity.Type.ONE_ON_ONE
                    } ?: false)
            }
        }
    }

    @DelicateKaliumApi(
        message = "Calling this function directly may cause conversation list to be displayed in an incorrect order",
        replaceWith = ReplaceWith("com.wire.kalium.logic.data.message.PersistMessageUseCase")
    )
    override suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationReadDate: Boolean,
        updateConversationModifiedDate: Boolean,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.insertOrIgnoreMessage(
            messageMapper.fromMessageToEntity(message),
            updateConversationReadDate,
            updateConversationModifiedDate
        )
    }

    override suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            messageDAO.deleteMessage(messageUuid, conversationId.toDao())
        }

    override suspend fun markMessageAsDeleted(messageUuid: String, conversationId: ConversationId): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageDAO.markMessageAsDeleted(id = messageUuid, conversationsId = conversationId.toDao())
        }

    override suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<CoreFailure, Message> =
        wrapStorageRequest {
            messageDAO.getMessageById(messageUuid, conversationId.toDao())
                .firstOrNull()?.run {
                    messageMapper.fromEntityToMessage(this)
                }
        }.onSuccess {
            Either.Right(it)
        }.onFailure {
            Either.Left(it)
        }

    override suspend fun getMessagesByConversationIdAndVisibilityAfterDate(
        conversationId: ConversationId,
        date: String,
        visibility: List<Message.Visibility>
    ): Flow<List<Message>> = messageDAO.observeMessagesByConversationAndVisibilityAfterDate(
        conversationId.toDao(),
        date,
        visibility.map { it.toEntityVisibility() }
    ).map { messageList -> messageList.map(messageMapper::fromEntityToMessage) }

    override suspend fun updateMessageStatus(messageStatus: MessageEntity.Status, conversationId: ConversationId, messageUuid: String) =
        wrapStorageRequest {
            messageDAO.updateMessageStatus(messageStatus, messageUuid, conversationId.toDao())
        }

    override suspend fun updateAssetMessageUploadStatus(
        uploadStatus: Message.UploadStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            messageDAO.updateAssetUploadStatus(
                assetMapper.fromUploadStatusToDaoModel(uploadStatus),
                messageUuid,
                conversationId.toDao()
            )
        }

    override suspend fun updateAssetMessageDownloadStatus(
        downloadStatus: Message.DownloadStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            messageDAO.updateAssetDownloadStatus(
                assetMapper.fromDownloadStatusToDaoModel(downloadStatus),
                messageUuid,
                conversationId.toDao()
            )
        }

    override suspend fun updateMessageDate(conversationId: ConversationId, messageUuid: String, date: String) =
        wrapStorageRequest {
            messageDAO.updateMessageDate(date, messageUuid, conversationId.toDao())
        }

    override suspend fun updatePendingMessagesAddMillisToDate(conversationId: ConversationId, millis: Long) =
        wrapStorageRequest {
            messageDAO.updateMessagesAddMillisToDate(millis, conversationId.toDao(), MessageEntity.Status.PENDING)
        }

    override suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget
    ): Either<CoreFailure, String> {
        val recipientMap = envelope.recipients.associate { recipientEntry ->
            recipientEntry.userId.toApi() to recipientEntry.clientPayloads.associate { clientPayload ->
                clientPayload.clientId.value to clientPayload.payload.data
            }
        }

        val messageOption = when (messageTarget) {
            is MessageTarget.Client -> MessageApi.QualifiedMessageOption.IgnoreAll
            is MessageTarget.Conversation -> MessageApi.QualifiedMessageOption.ReportAll
        }

        return wrapApiRequest {
            messageApi.qualifiedSendMessage(
                // TODO(messaging): Handle other MessageOptions, native push, transient and priorities
                MessageApi.Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap, true, MessagePriority.HIGH, false, envelope.dataBlob?.data,
                    messageOption
                ),
                conversationId.toApi(),
            )
        }.fold({ networkFailure ->
            val failure = when {
                (networkFailure is NetworkFailure.ServerMiscommunication && networkFailure.rootCause is ProteusClientsChangedError) -> {
                    sendMessageFailureMapper.fromDTO(networkFailure.rootCause as ProteusClientsChangedError)
                }

                else -> networkFailure
            }
            Either.Left(failure)
        }, {
            Either.Right(it.time)
        })
    }

    override suspend fun sendMLSMessage(conversationId: ConversationId, message: MLSMessageApi.Message): Either<CoreFailure, String> =
        wrapApiRequest {
            mlsMessageApi.sendMessage(message)
        }.flatMap { response ->
            Either.Right(response.time)
        }

    override suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>> = wrapStorageRequest {
        messageDAO.getAllPendingMessagesFromUser(senderUserId.toDao())
            .map(messageMapper::fromEntityToMessage)
    }

    override suspend fun getPendingConfirmationMessagesByConversationAfterDate(
        conversationId: ConversationId,
        visibility: List<Message.Visibility>
    ): Either<CoreFailure, List<String>> = wrapStorageRequest {
        messageDAO.getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(
            conversationId.toDao(),
            visibility.map { it.toEntityVisibility() }
        )
    }

    override suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editTimeStamp: String
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageDAO.updateTextMessageContent(
                editTimeStamp = editTimeStamp,
                conversationId = conversationId.toDao(),
                currentMessageId = messageContent.editMessageId,
                newTextContent = MessageEntityContent.Text(
                    messageContent.newContent,
                    messageContent.newMentions.map { messageMentionMapper.fromModelToDao(it) }
                ),
                newMessageId = newMessageId
            )
        }
    }

    override suspend fun resetAssetProgressStatus() {
        wrapStorageRequest {
            messageDAO.resetAssetUploadStatus()
            messageDAO.resetAssetDownloadStatus()
        }
    }

    override suspend fun markMessagesAsDecryptionResolved(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {

        messageDAO.markMessagesAsDecryptionResolved(
            conversationId = conversationId.toDao(),
            userId = userId.toDao(),
            clientId = clientId.value
        )
    }

    override suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ReceiptMode?> = wrapStorageRequest {
        messageDAO.getReceiptModeFromGroupConversationByQualifiedID(conversationId.toDao())
            .let {
                receiptModeMapper.fromEntityToModel(it)
            }
    }
}
