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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventEntity
import io.mockative.Mockable
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

enum class InsertMessageResult {
    INSERTED_INTO_MUTED_CONVERSATION, INSERTED_NEED_TO_NOTIFY_USER
}

@Suppress("TooManyFunctions")
@Mockable
interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun updateAssetTransferStatus(transferStatus: AssetTransferStatusEntity, id: String, conversationId: QualifiedIDEntity)
    suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity)
    suspend fun deleteAllMessages()

    /**
     * Inserts the message, or ignores if there's already a message with the same [MessageEntity.id] and [MessageEntity.conversationId].
     * There is only one exception where a second message with the same id will not be ignored, and it is when the first message is an asset
     * preview message. In this case, the second message containing the valid encryption keys will be updating and completing the encryption
     * keys and the visibility of the first one.
     *
     * @see insertOrIgnoreMessages
     */
    suspend fun insertOrIgnoreMessage(
        message: MessageEntity,
        updateConversationModifiedDate: Boolean = false
    ): InsertMessageResult

    /**
     * Inserts the messages, or ignores messages if there already exists a message with the same [MessageEntity.id] and
     * [MessageEntity.conversationId].
     * There is only one exception where a second message with the same id will not be ignored, and it is when the first message is an asset
     * preview message. In this case, the second message containing the valid encryption keys will be updating and completing the encryption
     * keys and the visibility of the first one.
     *
     * @see insertOrIgnoreMessage
     */
    suspend fun insertOrIgnoreMessages(messages: List<MessageEntity>, withUnreadEvents: Boolean = true)
    suspend fun persistSystemMessageToAllConversations(message: MessageEntity.System)
    suspend fun needsToBeNotified(id: String, conversationId: QualifiedIDEntity): Boolean
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)

    suspend fun updateMessagesStatus(status: MessageEntity.Status, id: List<String>, conversationId: QualifiedIDEntity)
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): MessageEntity?
    suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.entries
    ): Flow<List<MessageEntity>>

    suspend fun getLastMessagesByConversations(conversationIds: List<QualifiedIDEntity>): Map<QualifiedIDEntity, MessageEntity>

    suspend fun getNotificationMessage(maxNumberOfMessagesPerConversation: Int = 10): List<NotificationMessageEntity>

    suspend fun observeMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.entries
    ): Flow<List<MessageEntity>>

    suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity>
    suspend fun updateTextMessageContent(
        editInstant: Instant,
        conversationId: QualifiedIDEntity,
        currentMessageId: String,
        newTextContent: MessageEntityContent.Text,
        newMessageId: String
    )

    suspend fun updateLegalHoldMessageMembers(conversationId: QualifiedIDEntity, messageId: String, newMembers: List<QualifiedIDEntity>)

    suspend fun observeMessageVisibility(messageUuid: String, conversationId: QualifiedIDEntity): Flow<MessageEntity.Visibility?>
    suspend fun observeLastMessages(): Flow<List<MessagePreviewEntity>>

    suspend fun observeConversationsUnreadEvents(): Flow<List<ConversationUnreadEventEntity>>
    suspend fun observeUnreadEvents(): Flow<Map<ConversationIDEntity, List<UnreadEventEntity>>>
    suspend fun observeUnreadMessageCounter(): Flow<Map<ConversationIDEntity, Int>>

    suspend fun resetAssetTransferStatus()

    suspend fun markMessagesAsDecryptionResolved(
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        clientId: String,
    )

    suspend fun getMessageIdsThatExpectReadConfirmationWithinDates(
        conversationId: QualifiedIDEntity,
        afterDate: Instant,
        untilDate: Instant,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.entries
    ): List<String>

    suspend fun getReceiptModeFromGroupConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity.ReceiptMode?

    suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationIDEntity,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    )

    suspend fun getAllPendingEphemeralMessages(): List<MessageEntity>

    suspend fun getAllAlreadyEndedEphemeralMessages(): List<MessageEntity>

    suspend fun updateSelfDeletionEndDate(conversationId: QualifiedIDEntity, messageId: String, selfDeletionEndDate: Instant)

    suspend fun getConversationUnreadEventsCount(conversationId: QualifiedIDEntity): Long

    suspend fun insertFailedRecipientDelivery(
        id: String,
        conversationsId: QualifiedIDEntity,
        recipientsFailed: List<QualifiedIDEntity>,
        recipientFailureTypeEntity: RecipientFailureTypeEntity
    )

    suspend fun moveMessages(from: ConversationIDEntity, to: ConversationIDEntity)

    suspend fun getSearchedConversationMessagePosition(
        conversationId: QualifiedIDEntity,
        messageId: String
    ): Int

    val platformExtensions: MessageExtensions

    suspend fun getImageMessageAssets(
        conversationId: QualifiedIDEntity,
        mimeTypes: Set<String>,
        limit: Int,
        offset: Int
    ): List<AssetMessageEntity>

    suspend fun observeAssetStatuses(conversationId: QualifiedIDEntity): Flow<List<MessageAssetStatusEntity>>
    suspend fun getMessageAssetTransferStatus(messageId: String, conversationId: QualifiedIDEntity): AssetTransferStatusEntity
    suspend fun getAllMessageAssetIdsForConversationId(conversationId: QualifiedIDEntity): List<String>
    suspend fun getSenderNameById(id: String, conversationId: QualifiedIDEntity): String?
    suspend fun getNextAudioMessageInConversation(prevMessageId: String, conversationId: QualifiedIDEntity): String?
    fun getMessagesPaged(
        contentTypes: Collection<MessageEntity.ContentType>,
        pageSize: Int,
        onPage: (List<MessageEntity>) -> Unit,
    )
    fun countMessagesForBackup(contentTypes: Collection<MessageEntity.ContentType>): Long

    suspend fun updateMessagesStatusIfNotRead(status: MessageEntity.Status, conversationId: QualifiedIDEntity, messageIds: List<String>)
}
