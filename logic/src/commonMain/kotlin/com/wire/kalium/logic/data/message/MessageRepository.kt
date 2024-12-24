/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.asset.SUPPORTED_IMAGE_ASSET_MIME_TYPES
import com.wire.kalium.logic.data.asset.toDao
import com.wire.kalium.logic.data.asset.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ReceiptModeMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapper
import com.wire.kalium.logic.data.notification.LocalNotificationMessageMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.failure.ProteusSendMessageFailure
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.mapRight
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapFlowStorageRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.authenticated.message.MessagePriority
import com.wire.kalium.network.api.authenticated.message.Parameters
import com.wire.kalium.network.api.authenticated.message.QualifiedMessageOption
import com.wire.kalium.network.api.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import com.wire.kalium.persistence.dao.message.MessageDAO
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.RecipientFailureTypeEntity
import com.wire.kalium.util.DelicateKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

@Suppress("TooManyFunctions")
internal interface MessageRepository {
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
        updateConversationModifiedDate: Boolean = false,
    ): Either<CoreFailure, InsertMessageResult>

    suspend fun persistSystemMessageToAllConversations(
        message: Message.System
    ): Either<CoreFailure, Unit>

    suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit>
    suspend fun markMessageAsDeleted(messageUuid: String, conversationId: ConversationId): Either<StorageFailure, Unit>
    suspend fun updateMessageStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun updateMessagesStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuids: List<String>
    ): Either<CoreFailure, Unit>

    suspend fun updateAssetMessageTransferStatus(
        transferStatus: AssetTransferStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit>

    suspend fun getMessageById(conversationId: ConversationId, messageUuid: String): Either<StorageFailure, Message>

    suspend fun getMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
        visibility: List<Message.Visibility> = Message.Visibility.entries
    ): Flow<List<Message>>

    suspend fun getLastMessagesForConversationIds(
        conversationIdList: List<ConversationId>
    ): Either<StorageFailure, Map<ConversationId, Message>>

    suspend fun getNotificationMessage(messageSizePerConversation: Int = 10): Either<CoreFailure, List<LocalNotification>>

    suspend fun getMessagesByConversationIdAndVisibilityAfterDate(
        conversationId: ConversationId,
        date: String,
        visibility: List<Message.Visibility> = Message.Visibility.entries
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
        messageTarget: MessageTarget,
    ): Either<CoreFailure, MessageSent>

    /**
     * Send a Proteus [MessageEnvelope].
     *
     * @return [Either.Right] with the server date time in case of success
     * @return [Either.Left] of a [ProteusSendMessageFailure] if the server rejected the message
     * @return [Either.Left] of other [CoreFailure] for more generic cases
     */
    suspend fun broadcastEnvelope(
        envelope: MessageEnvelope,
        messageOption: BroadcastMessageOption
    ): Either<CoreFailure, Instant>

    suspend fun sendMLSMessage(
        conversationId: ConversationId,
        message: MLSMessageApi.Message
    ): Either<CoreFailure, MessageSent>

    suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>>
    suspend fun getPendingConfirmationMessagesByConversationAfterDate(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant,
        visibility: List<Message.Visibility> = Message.Visibility.entries
    ): Either<CoreFailure, List<String>>

    suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit>

    suspend fun updateLegalHoldMessageMembers(
        messageId: String,
        conversationId: ConversationId,
        newMembers: List<UserId>,
    ): Either<CoreFailure, Unit>

    suspend fun resetAssetTransferStatus()
    suspend fun markMessagesAsDecryptionResolved(
        conversationId: ConversationId,
        userId: UserId,
        clientId: ClientId,
    ): Either<CoreFailure, Unit>

    suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ReceiptMode?>

    /**
     * updates the message status to [MessageEntity.Status.SENT] and optionally sets the message creation date to [serverDate] if not null,
     * also marks other pending messages and adds millis to their date
     */
    suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationId,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ): Either<CoreFailure, Unit>

    suspend fun getAllPendingEphemeralMessages(): Either<CoreFailure, List<Message>>

    suspend fun getAllAlreadyEndedEphemeralMessages(): Either<CoreFailure, List<Message>>

    suspend fun markSelfDeletionEndDate(
        conversationId: ConversationId,
        messageUuid: String,
        deletionEndDate: Instant
    ): Either<CoreFailure, Unit>

    suspend fun observeMessageVisibility(
        messageUuid: String,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, MessageEntity.Visibility>>

    suspend fun persistRecipientsDeliveryFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit>

    suspend fun persistNoClientsToDeliverFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit>

    suspend fun moveMessagesToAnotherConversation(
        originalConversation: ConversationId,
        targetConversation: ConversationId
    ): Either<StorageFailure, Unit>

    suspend fun getSearchedConversationMessagePosition(
        conversationId: ConversationId,
        messageId: String
    ): Either<StorageFailure, Int>

    val extensions: MessageRepositoryExtensions
    suspend fun getImageAssetMessagesByConversationId(
        conversationId: ConversationId,
        limit: Int,
        offset: Int
    ): List<AssetMessage>

    suspend fun observeAssetStatuses(
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, List<MessageAssetStatus>>>

    suspend fun getMessageAssetTransferStatus(
        messageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, AssetTransferStatus>

    suspend fun getAllAssetIdsFromConversationId(
        conversationId: ConversationId,
    ): Either<StorageFailure, List<String>>

    suspend fun getSenderNameByMessageId(conversationId: ConversationId, messageId: String): Either<CoreFailure, String>
    suspend fun getNextAudioMessageInConversation(conversationId: ConversationId, messageId: String): Either<CoreFailure, String>
}

// TODO: suppress TooManyFunctions for now, something we need to fix in the future
@Suppress("LongParameterList", "TooManyFunctions")
internal class MessageDataSource internal constructor(
    private val selfUserId: UserId,
    private val messageApi: MessageApi,
    private val mlsMessageApi: MLSMessageApi,
    private val messageDAO: MessageDAO,
    private val sendMessageFailureMapper: SendMessageFailureMapper = MapperProvider.sendMessageFailureMapper(),
    private val messageMapper: MessageMapper = MapperProvider.messageMapper(selfUserId),
    private val linkPreviewMapper: LinkPreviewMapper = MapperProvider.linkPreviewMapper(),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
    private val receiptModeMapper: ReceiptModeMapper = MapperProvider.receiptModeMapper(),
    private val sendMessagePartialFailureMapper: SendMessagePartialFailureMapper = MapperProvider.sendMessagePartialFailureMapper(),
    private val notificationMapper: LocalNotificationMessageMapper = LocalNotificationMessageMapperImpl()
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

    override suspend fun getLastMessagesForConversationIds(
        conversationIdList: List<ConversationId>
    ): Either<StorageFailure, Map<ConversationId, Message>> = wrapStorageRequest {
        messageDAO.getLastMessagesByConversations(conversationIdList.map { it.toDao() })
    }.map { it.map { it.key.toModel() to messageMapper.fromEntityToMessage(it.value) }.toMap() }

    override suspend fun getImageAssetMessagesByConversationId(
        conversationId: ConversationId,
        limit: Int,
        offset: Int
    ): List<AssetMessage> = messageDAO.getImageMessageAssets(
        conversationId.toDao(),
        mimeTypes = SUPPORTED_IMAGE_ASSET_MIME_TYPES,
        limit,
        offset
    )
        .map(messageMapper::fromAssetEntityToAssetMessage)

    override suspend fun getNotificationMessage(
        messageSizePerConversation: Int
    ): Either<CoreFailure, List<LocalNotification>> = wrapStorageRequest {
        val notificationEntities = messageDAO.getNotificationMessage()
        notificationMapper.fromEntitiesToLocalNotifications(
            notificationEntities,
            messageSizePerConversation
        ) { message -> messageMapper.fromMessageToLocalNotificationMessage(message) }
    }

    @DelicateKaliumApi(
        message = "Calling this function directly may cause conversation list to be displayed in an incorrect order",
        replaceWith = ReplaceWith("com.wire.kalium.logic.data.message.PersistMessageUseCase")
    )
    override suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationModifiedDate: Boolean,
    ): Either<CoreFailure, InsertMessageResult> = wrapStorageRequest {
        messageDAO.insertOrIgnoreMessage(
            messageMapper.fromMessageToEntity(message),
            updateConversationModifiedDate
        )
    }

    override suspend fun persistSystemMessageToAllConversations(
        message: Message.System
    ): Either<CoreFailure, Unit> {
        messageMapper.fromMessageToEntity(message).let {
            return if (it is MessageEntity.System) {
                wrapStorageRequest {
                    messageDAO.persistSystemMessageToAllConversations(it)
                }
            } else Either.Left(CoreFailure.OnlySystemMessageAllowed)
        }
    }

    override suspend fun deleteMessage(messageUuid: String, conversationId: ConversationId): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            messageDAO.deleteMessage(messageUuid, conversationId.toDao())
        }

    override suspend fun markMessageAsDeleted(
        messageUuid: String,
        conversationId: ConversationId
    ): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            messageDAO.markMessageAsDeleted(id = messageUuid, conversationsId = conversationId.toDao())
        }

    override suspend fun getMessageById(
        conversationId: ConversationId,
        messageUuid: String
    ): Either<StorageFailure, Message> =
        wrapStorageRequest {
            messageDAO.getMessageById(messageUuid, conversationId.toDao())
        }.map(messageMapper::fromEntityToMessage)

    override suspend fun getMessagesByConversationIdAndVisibilityAfterDate(
        conversationId: ConversationId,
        date: String,
        visibility: List<Message.Visibility>
    ): Flow<List<Message>> = messageDAO.observeMessagesByConversationAndVisibilityAfterDate(
        conversationId.toDao(),
        date,
        visibility.map { it.toEntityVisibility() }
    ).map { messageList -> messageList.map(messageMapper::fromEntityToMessage) }

    override suspend fun updateMessageStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuid: String
    ) =
        wrapStorageRequest {
            messageDAO.updateMessageStatus(
                status = messageStatus,
                id = messageUuid,
                conversationId = conversationId.toDao()
            )
        }

    override suspend fun updateMessagesStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuids: List<String>
    ) =
        wrapStorageRequest {
            messageDAO.updateMessagesStatus(
                status = messageStatus,
                id = messageUuids,
                conversationId = conversationId.toDao()
            )
        }

    override suspend fun updateAssetMessageTransferStatus(
        transferStatus: AssetTransferStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            messageDAO.updateAssetTransferStatus(
                transferStatus.toDao(),
                messageUuid,
                conversationId.toDao()
            )
        }

    override suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget
    ): Either<CoreFailure, MessageSent> {
        val recipientMap: Map<NetworkQualifiedId, Map<String, ByteArray>> =
            envelope.recipients.associate { recipientEntry ->
                recipientEntry.userId.toApi() to recipientEntry.clientPayloads.associate { clientPayload ->
                    clientPayload.clientId.value to clientPayload.payload.data
                }
            }

        return wrapApiRequest {
            messageApi.qualifiedSendMessage(
                // TODO(messaging): Handle other MessageOptions, native push, transient and priorities
                Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap,
                    true,
                    MessagePriority.HIGH,
                    false,
                    envelope.dataBlob?.data,
                    messageTarget.toOption()
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
        }, { response: QualifiedSendMessageResponse ->
            Either.Right(sendMessagePartialFailureMapper.fromDTO(response))
        })
    }

    private fun MessageTarget.toOption() = when (this) {
        is MessageTarget.Client -> QualifiedMessageOption.IgnoreAll

        is MessageTarget.Conversation -> if (this.usersToIgnore.isNotEmpty()) {
            QualifiedMessageOption.IgnoreSome(this.usersToIgnore.map { it.toApi() })
        } else {
            QualifiedMessageOption.ReportAll
        }

        is MessageTarget.Users -> QualifiedMessageOption.ReportSome(this.userId.map { it.toApi() })
    }

    override suspend fun broadcastEnvelope(
        envelope: MessageEnvelope,
        messageOption: BroadcastMessageOption
    ): Either<CoreFailure, Instant> {
        val recipientMap: Map<NetworkQualifiedId, Map<String, ByteArray>> =
            envelope.recipients.associate { recipientEntry ->
                recipientEntry.userId.toApi() to recipientEntry.clientPayloads.associate { clientPayload ->
                    clientPayload.clientId.value to clientPayload.payload.data
                }
            }

        val option = when (messageOption) {
            is BroadcastMessageOption.IgnoreSome -> QualifiedMessageOption.IgnoreSome(messageOption.userIDs.map { it.toApi() })
            is BroadcastMessageOption.ReportSome -> QualifiedMessageOption.ReportSome(messageOption.userIDs.map { it.toApi() })
            is BroadcastMessageOption.ReportAll -> QualifiedMessageOption.ReportAll
            is BroadcastMessageOption.IgnoreAll -> QualifiedMessageOption.IgnoreAll
        }

        return wrapApiRequest {
            messageApi.qualifiedBroadcastMessage(
                Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap,
                    true,
                    MessagePriority.HIGH,
                    false,
                    envelope.dataBlob?.data,
                    option
                ),
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

    override suspend fun sendMLSMessage(
        conversationId: ConversationId,
        message: MLSMessageApi.Message
    ): Either<CoreFailure, MessageSent> =
        wrapApiRequest {
            mlsMessageApi.sendMessage(message)
        }.flatMap { response ->
            Either.Right(sendMessagePartialFailureMapper.fromMlsDTO(response))
        }

    override suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>> =
        wrapStorageRequest {
            messageDAO.getAllPendingMessagesFromUser(senderUserId.toDao())
                .map(messageMapper::fromEntityToMessage)
        }

    override suspend fun getPendingConfirmationMessagesByConversationAfterDate(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant,
        visibility: List<Message.Visibility>
    ): Either<CoreFailure, List<String>> = wrapStorageRequest {
        messageDAO.getMessageIdsThatExpectReadConfirmationWithinDates(
            conversationId.toDao(),
            afterDateTime,
            untilDateTime,
            visibility.map { it.toEntityVisibility() }
        )
    }

    override suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageDAO.updateTextMessageContent(
                editInstant = editInstant,
                conversationId = conversationId.toDao(),
                currentMessageId = messageContent.editMessageId,
                newTextContent = MessageEntityContent.Text(
                    messageContent.newContent,
                    messageContent.newLinkPreviews.map { linkPreviewMapper.fromModelToDao(it) },
                    messageContent.newMentions.map { messageMentionMapper.fromModelToDao(it) }
                ),
                newMessageId = newMessageId
            )
        }
    }

    override suspend fun updateLegalHoldMessageMembers(
        messageId: String,
        conversationId: ConversationId,
        newMembers: List<UserId>,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.updateLegalHoldMessageMembers(conversationId.toDao(), messageId, newMembers.map { it.toDao() })
    }

    override suspend fun resetAssetTransferStatus() {
        wrapStorageRequest {
            messageDAO.resetAssetTransferStatus()
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
            ?.let { receiptModeMapper.fromEntityToModel(it) }
    }

    override suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationId,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        messageDAO.promoteMessageToSentUpdatingServerTime(
            conversationId.toDao(),
            messageUuid,
            serverDate,
            millis
        )
    }

    override suspend fun getAllPendingEphemeralMessages(): Either<CoreFailure, List<Message>> =
        wrapStorageRequest {
            messageDAO.getAllPendingEphemeralMessages().map(messageMapper::fromEntityToMessage)
        }

    override suspend fun getAllAlreadyEndedEphemeralMessages(): Either<CoreFailure, List<Message>> =
        wrapStorageRequest {
            messageDAO
                .getAllAlreadyEndedEphemeralMessages()
                .map(messageMapper::fromEntityToMessage)
        }

    override suspend fun markSelfDeletionEndDate(
        conversationId: ConversationId,
        messageUuid: String,
        deletionEndDate: Instant
    ): Either<CoreFailure, Unit> {
        return wrapStorageRequest {
            messageDAO.updateSelfDeletionEndDate(conversationId.toDao(), messageUuid, deletionEndDate)
        }
    }

    override suspend fun observeMessageVisibility(
        messageUuid: String,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, MessageEntity.Visibility>> =
        wrapFlowStorageRequest {
            messageDAO.observeMessageVisibility(messageUuid, conversationId.toDao())
        }

    /**
     * Persist a list of users ids that failed to receive the message
     * [RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED]
     */
    override suspend fun persistRecipientsDeliveryFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>,
    ): Either<CoreFailure, Unit> = wrapStorageRequest({
        kaliumLogger.w("Ignoring failed recipients for this 'not' Message.Regular: ${it.message.orEmpty()})")
        Either.Right(Unit)
    }) {
        messageDAO.insertFailedRecipientDelivery(
            messageUuid,
            conversationId.toDao(),
            usersWithFailedDeliveryList.map { it.toDao() },
            RecipientFailureTypeEntity.MESSAGE_DELIVERY_FAILED
        )
    }

    /**
     * Persist a list of users ids whose clients are missing and could not be retrieved
     * [RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER]
     */
    override suspend fun persistNoClientsToDeliverFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit> = wrapStorageRequest({
        kaliumLogger.w("Ignoring failed recipients for this 'not' Message.Regular : ${it.message.orEmpty()})")
        Either.Right(Unit)
    }) {
        messageDAO.insertFailedRecipientDelivery(
            messageUuid,
            conversationId.toDao(),
            usersWithFailedDeliveryList.map { it.toDao() },
            RecipientFailureTypeEntity.NO_CLIENTS_TO_DELIVER
        )
    }

    override suspend fun moveMessagesToAnotherConversation(
        originalConversation: ConversationId,
        targetConversation: ConversationId
    ): Either<StorageFailure, Unit> = wrapStorageRequest {
        messageDAO.moveMessages(
            from = originalConversation.toDao(),
            to = targetConversation.toDao()
        )
    }

    override suspend fun getSearchedConversationMessagePosition(
        conversationId: ConversationId,
        messageId: String
    ): Either<StorageFailure, Int> = wrapStorageRequest {
        messageDAO.getSearchedConversationMessagePosition(
            conversationId = conversationId.toDao(),
            messageId = messageId
        )
    }

    override suspend fun observeAssetStatuses(
        conversationId: ConversationId
    ) = messageDAO.observeAssetStatuses(conversationId.toDao())
        .wrapStorageRequest()
        .mapRight { assetStatusEntities -> assetStatusEntities.map { it.toModel() } }

    override suspend fun getMessageAssetTransferStatus(
        messageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, AssetTransferStatus> = wrapStorageRequest {
        messageDAO.getMessageAssetTransferStatus(messageId, conversationId.toDao()).toModel()
    }

    override suspend fun getAllAssetIdsFromConversationId(
        conversationId: ConversationId
    ): Either<StorageFailure, List<String>> {
        return wrapStorageRequest {
            messageDAO.getAllMessageAssetIdsForConversationId(conversationId = conversationId.toDao())
        }
    }

    override suspend fun getSenderNameByMessageId(conversationId: ConversationId, messageId: String): Either<CoreFailure, String> =
        wrapStorageRequest { messageDAO.getSenderNameById(messageId, conversationId.toDao()) }

    override suspend fun getNextAudioMessageInConversation(
        conversationId: ConversationId,
        messageId: String
    ): Either<CoreFailure, String> =
        wrapStorageRequest { messageDAO.getNextAudioMessageInConversation(messageId, conversationId.toDao()) }
}
