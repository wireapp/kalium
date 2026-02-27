/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.fakes

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageAssetStatus
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.MessageRepositoryExtensions
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.notification.LocalNotification
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.framework.TestMessage
import com.wire.kalium.messaging.sending.MessageTarget
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.persistence.dao.message.InsertMessageResult
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Instant

internal open class FakeMessageRepository : MessageRepository {

    override val extensions: MessageRepositoryExtensions
        get() = FakeMessageRepositoryExtensions()

    override suspend fun persistMessage(
        message: Message.Standalone,
        updateConversationModifiedDate: Boolean
    ): Either<CoreFailure, InsertMessageResult> = InsertMessageResult.INSERTED_INTO_MUTED_CONVERSATION.right()

    override suspend fun persistSystemMessageToAllConversations(message: Message.System): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun deleteMessage(
        messageUuid: String,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun markMessageAsDeleted(
        messageUuid: String,
        conversationId: ConversationId
    ): Either<StorageFailure, Unit> = Unit.right()

    override suspend fun updateMessageStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun updateMessagesStatus(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageUuids: List<String>
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun updateAssetMessageTransferStatus(
        transferStatus: AssetTransferStatus,
        conversationId: ConversationId,
        messageUuid: String
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun getMessageById(
        conversationId: ConversationId,
        messageUuid: String
    ): Either<StorageFailure, Message> = TestMessage.TEXT_MESSAGE.right()

    override suspend fun observeMessageById(
        conversationId: ConversationId,
        messageUuid: String
    ): Flow<Either<StorageFailure, Message>> = flowOf(TestMessage.TEXT_MESSAGE.right())

    override suspend fun getMessagesByConversationIdAndVisibility(
        conversationId: ConversationId,
        limit: Int,
        offset: Int,
        visibility: List<Message.Visibility>
    ): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun getLastMessagesForConversationIds(
        conversationIdList: List<ConversationId>
    ): Either<StorageFailure, Map<ConversationId, Message>> = emptyMap<ConversationId, Message>().right()

    override suspend fun getNotificationMessage(messageSizePerConversation: Int): Either<CoreFailure, List<LocalNotification>> =
        emptyList<LocalNotification>().right()

    override suspend fun getMessagesByConversationIdAndVisibilityAfterDate(
        conversationId: ConversationId,
        date: String,
        visibility: List<Message.Visibility>
    ): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget
    ): Either<CoreFailure, MessageSent> = TestMessage.TEST_MESSAGE_SENT.right()


    override suspend fun broadcastEnvelope(
        envelope: MessageEnvelope,
        messageOption: BroadcastMessageOption
    ): Either<CoreFailure, Instant> = TestMessage.TEST_DATE.right()

    override suspend fun sendMLSMessage(message: MLSMessageApi.Message): Either<CoreFailure, MessageSent> =
        TestMessage.TEST_MESSAGE_SENT.right()

    override suspend fun getAllPendingMessagesFromUser(senderUserId: UserId): Either<CoreFailure, List<Message>> =
        emptyList<Message>().right()


    override suspend fun getPendingConfirmationMessagesByConversationAfterDate(
        conversationId: ConversationId,
        afterDateTime: Instant,
        untilDateTime: Instant,
        visibility: List<Message.Visibility>
    ): Either<CoreFailure, List<String>> = emptyList<String>().right()

    override suspend fun updateTextMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.TextEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun updateLegalHoldMessageMembers(
        messageId: String,
        conversationId: ConversationId,
        newMembers: List<UserId>
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun updateMultipartMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.MultipartEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun resetAssetTransferStatus() {}

    override suspend fun markProteusMessagesAsDecryptionResolved(
        userId: UserId,
        clientId: ClientId
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun getReceiptModeFromGroupConversationByQualifiedID(
        conversationId: ConversationId
    ): Either<CoreFailure, Conversation.ReceiptMode?> = null.right()

    override suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationId,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun getAllPendingEphemeralMessages(): Either<CoreFailure, List<Message>> = emptyList<Message>().right()

    override suspend fun getAllAlreadyEndedEphemeralMessages(): Either<CoreFailure, List<Message>> = emptyList<Message>().right()

    override suspend fun markSelfDeletionEndDate(
        conversationId: ConversationId,
        messageUuid: String,
        deletionEndDate: Instant
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun observeMessageVisibility(
        messageUuid: String,
        conversationId: ConversationId
    ): Flow<Either<StorageFailure, MessageEntity.Visibility>> {
        TODO("Not yet implemented")
    }

    override suspend fun persistRecipientsDeliveryFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun persistNoClientsToDeliverFailure(
        conversationId: ConversationId,
        messageUuid: String,
        usersWithFailedDeliveryList: List<UserId>
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun moveMessagesToAnotherConversation(
        originalConversation: ConversationId,
        targetConversation: ConversationId
    ): Either<StorageFailure, Unit> = Unit.right()

    override suspend fun getSearchedConversationMessagePosition(
        conversationId: ConversationId,
        messageId: String
    ): Either<StorageFailure, Int> = 0.right()

    override suspend fun getImageAssetMessagesByConversationId(
        conversationId: ConversationId,
        limit: Int,
        offset: Int
    ): List<AssetMessage> = emptyList()

    override suspend fun observeAssetStatuses(conversationId: ConversationId): Flow<Either<StorageFailure, List<MessageAssetStatus>>> =
        flowOf(emptyList<MessageAssetStatus>().right())

    override suspend fun getMessageAssetTransferStatus(
        messageId: String,
        conversationId: ConversationId
    ): Either<StorageFailure, AssetTransferStatus> = AssetTransferStatus.NOT_DOWNLOADED.right()

    override suspend fun getAllAssetIdsFromConversationId(conversationId: ConversationId): Either<StorageFailure, List<String>> =
        emptyList<String>().right()

    override suspend fun getSenderNameByMessageId(
        conversationId: ConversationId,
        messageId: String
    ): Either<CoreFailure, String> = "".right()

    override suspend fun getNextAudioMessageInConversation(
        conversationId: ConversationId,
        messageId: String
    ): Either<CoreFailure, String>  = "".right()

    override suspend fun updateMessagesStatusIfNotRead(
        messageStatus: MessageEntity.Status,
        conversationId: ConversationId,
        messageIds: List<String>
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun updateCompositeMessage(
        conversationId: ConversationId,
        messageContent: MessageContent.CompositeEdited,
        newMessageId: String,
        editInstant: Instant
    ): Either<StorageFailure, Unit> = Unit.right()

    override suspend fun observeAssetStatuses(): Flow<Either<StorageFailure, List<AssetTransferStatus>>> =
        flowOf(emptyList<AssetTransferStatus>().right())

    override suspend fun updateAudioMessageNormalizedLoudness(
        conversationId: ConversationId,
        messageId: String,
        normalizedLoudness: ByteArray
    ): Either<CoreFailure, Unit> = Unit.right()

    override suspend fun searchMessagesByText(
        conversationId: ConversationId,
        searchQuery: String,
        limit: Int,
        offset: Int
    ): Either<StorageFailure, List<Message.Standalone>> = emptyList<Message.Standalone>().right()

    override suspend fun searchMessagesByTextGlobally(
        searchQuery: String,
        limit: Int,
        offset: Int
    ): Either<StorageFailure, List<Message.Standalone>> = emptyList<Message.Standalone>().right()
}
