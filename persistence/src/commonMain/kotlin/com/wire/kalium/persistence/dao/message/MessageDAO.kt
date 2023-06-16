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

package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

@Suppress("TooManyFunctions")
interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun updateAssetUploadStatus(uploadStatus: MessageEntity.UploadStatus, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateAssetDownloadStatus(downloadStatus: MessageEntity.DownloadStatus, id: String, conversationId: QualifiedIDEntity)
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
        updateConversationReadDate: Boolean = false,
        updateConversationModifiedDate: Boolean = false
    )

    /**
     * Inserts the messages, or ignores messages if there already exists a message with the same [MessageEntity.id] and
     * [MessageEntity.conversationId].
     * There is only one exception where a second message with the same id will not be ignored, and it is when the first message is an asset
     * preview message. In this case, the second message containing the valid encryption keys will be updating and completing the encryption
     * keys and the visibility of the first one.
     *
     * @see insertOrIgnoreMessage
     */
    suspend fun insertOrIgnoreMessages(messages: List<MessageEntity>)
    suspend fun persistSystemMessageToAllConversations(message: MessageEntity.System)
    suspend fun needsToBeNotified(id: String, conversationId: QualifiedIDEntity): Boolean
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): MessageEntity?
    suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): Flow<List<MessageEntity>>

    suspend fun getNotificationMessage(): Flow<List<NotificationMessageEntity>>

    suspend fun observeMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): Flow<List<MessageEntity>>

    suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity>
    suspend fun updateTextMessageContent(
        editTimeStamp: String,
        conversationId: QualifiedIDEntity,
        currentMessageId: String,
        newTextContent: MessageEntityContent.Text,
        newMessageId: String
    )

    suspend fun observeMessageVisibility(messageUuid: String, conversationId: QualifiedIDEntity): Flow<MessageEntity.Visibility?>

    suspend fun getConversationMessagesByContentType(
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType
    ): List<MessageEntity>

    suspend fun deleteAllConversationMessages(conversationId: QualifiedIDEntity)

    suspend fun observeLastMessages(): Flow<List<MessagePreviewEntity>>

    suspend fun observeConversationsUnreadEvents(): Flow<List<ConversationUnreadEventEntity>>
    suspend fun observeUnreadEvents(): Flow<Map<ConversationIDEntity, List<UnreadEventEntity>>>
    suspend fun observeUnreadMessageCounter(): Flow<Map<ConversationIDEntity, Int>>

    suspend fun resetAssetUploadStatus()

    suspend fun resetAssetDownloadStatus()

    suspend fun markMessagesAsDecryptionResolved(
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        clientId: String,
    )

    suspend fun getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): List<String>

    suspend fun getReceiptModeFromGroupConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity.ReceiptMode?

    suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationIDEntity,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    )

    suspend fun getEphemeralMessagesMarkedForDeletion(): List<MessageEntity>

    suspend fun updateSelfDeletionStartDate(conversationId: QualifiedIDEntity, messageId: String, selfDeletionStartDate: Instant)

    suspend fun getConversationUnreadEventsCount(conversationId: QualifiedIDEntity): Long

    val platformExtensions: MessageExtensions
}
