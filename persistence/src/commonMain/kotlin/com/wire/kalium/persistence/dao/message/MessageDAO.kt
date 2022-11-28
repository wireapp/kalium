package com.wire.kalium.persistence.dao.message

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
    suspend fun insertMessage(
        message: MessageEntity,
        updateConversationReadDate: Boolean = false,
        updateConversationModifiedDate: Boolean = false,
        updateConversationNotificationsDate: Boolean = false
    )

    suspend fun insertMessages(messages: List<MessageEntity>)
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

    suspend fun getMessagesByConversationAndVisibilityAfterDate(
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

    suspend fun observeLastUnreadMessage(
        conversationID: QualifiedIDEntity
    ): Flow<MessageEntity?>

    suspend fun observeUnreadMentionsCount(conversationId: QualifiedIDEntity): Flow<Long>

    suspend fun resetAssetUploadStatus()

    suspend fun resetAssetDownloadStatus()

    val platformExtensions: MessageExtensions
}
