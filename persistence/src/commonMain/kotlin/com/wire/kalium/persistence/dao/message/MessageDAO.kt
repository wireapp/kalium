package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
interface MessageDAO {
    suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity)
    suspend fun updateAssetUploadStatus(uploadStatus: MessageEntity.UploadStatus, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateAssetDownloadStatus(downloadStatus: MessageEntity.DownloadStatus, id: String, conversationId: QualifiedIDEntity)
    suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity)
    suspend fun markAsEdited(editTimeStamp: String, conversationId: QualifiedIDEntity, id: String)
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
    suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessageId(conversationId: QualifiedIDEntity, oldMessageId: String, newMessageId: String)
    suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity)
    suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status)
    suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?>
    suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): Flow<List<MessageEntity>>

    suspend fun getNotificationMessage(
        filteredContent: List<MessageEntity.ContentType>
    ): Flow<List<NotificationMessageEntity>>

    suspend fun observeMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): Flow<List<MessageEntity>>

    suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity>
    suspend fun updateTextMessageContent(
        conversationId: QualifiedIDEntity,
        messageId: String,
        newTextContent: MessageEntityContent.Text
    )

    suspend fun getConversationMessagesByContentType(
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType
    ): List<MessageEntity>

    suspend fun deleteAllConversationMessages(conversationId: QualifiedIDEntity)

    suspend fun observeLastMessages(): Flow<List<MessagePreviewEntity>>

    suspend fun observeUnreadMessages(): Flow<List<MessagePreviewEntity>>

    suspend fun resetAssetUploadStatus()

    suspend fun resetAssetDownloadStatus()
    suspend fun observeMessageVisibility(messageUuid: String, conversationId: QualifiedIDEntity): Flow<MessageEntity.Visibility>

    suspend fun markMessagesAsDecryptionResolved(
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        clientId: String,
    )

    suspend fun getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility> = MessageEntity.Visibility.values().toList()
    ): List<MessageEntity>

    suspend fun getReceiptModeFromGroupConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity.ReceiptMode?

    val platformExtensions: MessageExtensions
}
