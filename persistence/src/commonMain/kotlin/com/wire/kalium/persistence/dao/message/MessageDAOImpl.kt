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

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class MessageDAOImpl(
    private val queries: MessagesQueries,
    private val conversationsQueries: ConversationsQueries,
    private val selfUserId: UserIDEntity,
    private val reactionsQueries: ReactionsQueries,
    private val coroutineContext: CoroutineContext
) : MessageDAO, MessageInsertExtension by MessageInsertExtensionImpl(queries) {
    private val mapper = MessageMapper

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = withContext(coroutineContext) {
        queries.deleteMessage(id, conversationsId)
    }

    override suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity) =
        withContext(coroutineContext) {
            queries.markMessageAsDeleted(id, conversationsId)
        }

    override suspend fun deleteAllMessages() = withContext(coroutineContext) {
        queries.deleteAllMessages()
    }

    override suspend fun insertOrIgnoreMessage(
        message: MessageEntity,
        updateConversationReadDate: Boolean,
        updateConversationModifiedDate: Boolean
    ) = withContext(coroutineContext) {
        queries.transaction {
            val messageCreationInstant = message.date
            if (updateConversationReadDate) {
                conversationsQueries.updateConversationReadDate(messageCreationInstant, message.conversationId)
            }

            insertInDB(message)

            if (!nonSuspendNeedsToBeNotified(message.id, message.conversationId)) {
                conversationsQueries.updateConversationNotificationsDate(messageCreationInstant, message.conversationId)
            }

            if (updateConversationModifiedDate) {
                conversationsQueries.updateConversationModifiedDate(messageCreationInstant, message.conversationId)
            }
        }
    }

    override suspend fun needsToBeNotified(id: String, conversationId: QualifiedIDEntity) = withContext(coroutineContext) {
        nonSuspendNeedsToBeNotified(id, conversationId)
    }

    private fun nonSuspendNeedsToBeNotified(id: String, conversationId: QualifiedIDEntity) =
        queries.needsToBeNotified(id, conversationId).executeAsOne() == 1L

    @Deprecated("For test only!")
    override suspend fun insertOrIgnoreMessages(messages: List<MessageEntity>) = withContext(coroutineContext) {
        queries.transaction {
            messages.forEach { insertInDB(it) }
        }
    }

    override suspend fun persistSystemMessageToAllConversations(message: MessageEntity.System) {
        queries.insertOrIgnoreBulkSystemMessage(
            id = message.id,
            creation_date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = null,
            visibility = message.visibility,
            status = message.status,
            content_type = contentTypeOf(message.content),
            expects_read_confirmation = false
        )
    }

    /**
     * Be careful and run this operation in ONE wrapping transaction.
     */
    private fun insertInDB(message: MessageEntity) {
        // do not add withContext
        if (!updateIdIfAlreadyExists(message)) {
            if (isValidAssetMessageUpdate(message)) {
                updateAssetMessage(message)
                return
            } else {
                insertMessageOrIgnore(message)
            }
        }
    }

    /*
        When the user leaves a group, the app generates MemberChangeType.REMOVED and saves it locally because the socket doesn't send such
        message for the author of the change, so it's generated by the app and stored with local id, but the REST request to get all events
        the user missed when offline still returns this event, so in order to avoid duplicates and to have a valid remote id, the app needs
        to check and replace the id of the already existing system message instead of adding another one.
        This behavior is similar for all requests which generate events, for now member-join ,member-leave and rename are handled.
    */
    private fun updateIdIfAlreadyExists(message: MessageEntity): Boolean =
        when (message.content) {
            is MessageEntityContent.MemberChange, is MessageEntityContent.ConversationRenamed -> message.content
            else -> null
        }?.let {
            if (message.senderUserId == selfUserId) it else null
        }?.let { messageContent ->
            // Check if the message with given time and type already exists in the local DB.
            queries.selectByConversationIdAndSenderIdAndTimeAndType(
                message.conversationId,
                message.senderUserId,
                message.date,
                contentTypeOf(messageContent)
            )
                .executeAsList()
                .firstOrNull {
                    LocalId.check(it.id) && when (messageContent) {
                        is MessageEntityContent.MemberChange ->
                            messageContent.memberChangeType == it.memberChangeType &&
                                    it.memberChangeList?.toSet() == messageContent.memberUserIdList.toSet()

                        is MessageEntityContent.ConversationRenamed ->
                            it.conversationName == messageContent.conversationName

                        else -> false
                    }
                }?.let {
                    // The message already exists in the local DB, if its id is different then just update id.
                    if (it.id != message.id) queries.updateMessageId(message.id, it.id, message.conversationId)
                    true
                }
        } ?: false

    override suspend fun updateAssetUploadStatus(
        uploadStatus: MessageEntity.UploadStatus,
        id: String,
        conversationId: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        queries.updateAssetUploadStatus(uploadStatus, id, conversationId)
    }

    override suspend fun updateAssetDownloadStatus(
        downloadStatus: MessageEntity.DownloadStatus,
        id: String,
        conversationId: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        queries.updateAssetDownloadStatus(downloadStatus, id, conversationId)
    }

    override suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity) =
        withContext(coroutineContext) {
            queries.updateMessageStatus(status, id, conversationId)
        }

    override suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity) =
        withContext(coroutineContext) {
            queries.updateMessageDate(date.toInstant(), id, conversationId)
        }

    override suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status) =
        withContext(coroutineContext) {
            queries.updateMessagesAddMillisToDate(Instant.fromEpochMilliseconds(millis), conversationId, status)
        }

    // TODO: mark internal since it is used for tests only
    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?> =
        queries.selectById(id, conversationId, mapper::toEntityMessageFromView)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToOneOrNull()

    override suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>> =
        queries.selectByConversationIdAndVisibility(
            conversationId,
            visibility,
            limit.toLong(),
            offset.toLong(),
            mapper::toEntityMessageFromView
        )
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()

    override suspend fun getNotificationMessage(
        filteredContent: List<MessageEntity.ContentType>
    ): Flow<List<NotificationMessageEntity>> =
        queries.getNotificationsMessages(
            filteredContent,
            mapper::toNotificationEntity
        ).asFlow()
            .flowOn(coroutineContext)
            .mapToList()

    override suspend fun observeMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>> =
        queries.selectMessagesByConversationIdAndVisibilityAfterDate(
            conversationId, visibility, date.toInstant(),
            mapper::toEntityMessageFromView
        )
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()

    override suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity> =
        withContext(coroutineContext) {
            queries.selectMessagesFromUserByStatus(
                userId, MessageEntity.Status.PENDING,
                mapper::toEntityMessageFromView
            )
                .executeAsList()
        }

    override suspend fun updateTextMessageContent(
        editTimeStamp: String,
        conversationId: QualifiedIDEntity,
        currentMessageId: String,
        newTextContent: MessageEntityContent.Text,
        newMessageId: String
    ): Unit = withContext(coroutineContext) {
        queries.transaction {
            queries.markMessageAsEdited(editTimeStamp.toInstant(), currentMessageId, conversationId)
            reactionsQueries.deleteAllReactionsForMessage(currentMessageId, conversationId)
            queries.deleteMessageMentions(currentMessageId, conversationId)
            queries.updateMessageTextContent(newTextContent.messageBody, currentMessageId, conversationId)
            newTextContent.mentions.forEach {
                queries.insertMessageMention(
                    message_id = currentMessageId,
                    conversation_id = conversationId,
                    start = it.start,
                    length = it.length,
                    user_id = it.userId
                )
            }
            queries.updateMessageId(newMessageId, currentMessageId, conversationId)
            queries.updateQuotedMessageId(newMessageId, currentMessageId, conversationId)
        }
    }

    override suspend fun getConversationMessagesByContentType(
        conversationId: QualifiedIDEntity,
        contentType: MessageEntity.ContentType
    ): List<MessageEntity> = withContext(coroutineContext) {
        queries.getConversationMessagesByContentType(conversationId, contentType, mapper::toEntityMessageFromView)
            .executeAsList()
    }

    override suspend fun deleteAllConversationMessages(conversationId: QualifiedIDEntity) {
        withContext(coroutineContext) {
            queries.deleteAllConversationMessages(conversationId)
        }
    }

    override suspend fun observeLastMessages(): Flow<List<MessagePreviewEntity>> =
        queries.getLastMessages(mapper::toPreviewEntity).asFlow().flowOn(coroutineContext).mapToList()

    override suspend fun observeUnreadMessages(): Flow<List<MessagePreviewEntity>> =
        flowOf(emptyList())
    // FIXME: Re-enable gradually as we improve its performance
    //        queries.getUnreadMessages(mapper::toPreviewEntity).asFlow().flowOn(coroutineContext).mapToList()

    override suspend fun observeUnreadMessageCounter(): Flow<Map<ConversationIDEntity, Int>> =
        queries.getUnreadMessagesCount { conversationId, count ->
            conversationId to count.toInt()
        }.asFlow().flowOn(coroutineContext).mapToList().map { it.toMap() }

    override suspend fun resetAssetDownloadStatus() = withContext(coroutineContext) {
        queries.resetAssetDownloadStatus()
    }

    override suspend fun markMessagesAsDecryptionResolved(
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        clientId: String,
    ) = withContext(coroutineContext) {
        queries.markMessagesAsDecryptionResolved(userId, clientId)
    }

    override suspend fun resetAssetUploadStatus() = withContext(coroutineContext) {
        queries.resetAssetUploadStatus()
    }

    override suspend fun getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        visibility: List<MessageEntity.Visibility>
    ): List<String> = withContext(coroutineContext) {
        queries.selectPendingMessagesIdsByConversationIdAndVisibilityAfterDate(
            conversationId, visibility
        ).executeAsList()
    }

    override suspend fun getReceiptModeFromGroupConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity.ReceiptMode? =
        withContext(coroutineContext) {
            conversationsQueries.selectReceiptModeFromGroupConversationByQualifiedId(qualifiedID)
                .executeAsOneOrNull()
        }

    override val platformExtensions: MessageExtensions = MessageExtensionsImpl(queries, mapper, coroutineContext)

}
