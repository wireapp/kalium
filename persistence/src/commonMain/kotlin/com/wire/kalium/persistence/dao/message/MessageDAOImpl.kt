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
import com.wire.kalium.persistence.MessagePreviewQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.NotificationQueries
import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.UnreadEventsQueries
import com.wire.kalium.persistence.content.ButtonContentQueries
import com.wire.kalium.persistence.dao.ConversationIDEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.unread.ConversationUnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventMapper
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions", "LongParameterList")
internal class MessageDAOImpl internal constructor(
    private val queries: MessagesQueries,
    private val notificationQueries: NotificationQueries,
    private val conversationsQueries: ConversationsQueries,
    private val unreadEventsQueries: UnreadEventsQueries,
    private val messagePreviewQueries: MessagePreviewQueries,
    private val selfUserId: UserIDEntity,
    private val reactionsQueries: ReactionsQueries,
    private val coroutineContext: CoroutineContext,
    buttonContentQueries: ButtonContentQueries
) : MessageDAO,
    MessageInsertExtension by MessageInsertExtensionImpl(
        queries,
        unreadEventsQueries,
        conversationsQueries,
        buttonContentQueries,
        selfUserId
    ) {
    private val mapper = MessageMapper
    private val unreadEventMapper = UnreadEventMapper

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = withContext(coroutineContext) {
        queries.deleteMessage(id, conversationsId)
    }

    override suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity) =
        withContext(coroutineContext) {
            queries.markMessageAsDeleted(id, conversationsId)
            unreadEventsQueries.deleteUnreadEvent(id, conversationsId)
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
                unreadEventsQueries.deleteUnreadEvents(message.date, message.conversationId)
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
        queries.needsToBeNotified(id, conversationId).executeAsList().first() == 1L

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

    /**
    When the user leaves a group, the app generates MemberChangeType.REMOVED and saves it locally because the socket doesn't send such
    message for the author of the change, so it's generated by the app and stored with local id, but the REST request to get all events
    the user missed when offline still returns this event, so in order to avoid duplicates and to have a valid remote id, the app needs
    to check and replace the id of the already existing system message instead of adding another one.
    This behavior is similar for all requests which generate events:
    - [MessageEntityContent.MemberChange]
    - [MessageEntityContent.ConversationRenamed]
    - [MessageEntityContent.ConversationMessageTimerChanged]
     */
    @Suppress("ComplexMethod")
    private fun updateIdIfAlreadyExists(message: MessageEntity): Boolean =
        when (message.content) {
            is MessageEntityContent.MemberChange, is MessageEntityContent.ConversationRenamed,
            is MessageEntityContent.ConversationMessageTimerChanged -> message.content

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

                        is MessageEntityContent.ConversationMessageTimerChanged ->
                            it.messageTimerChanged == messageContent.messageTimer

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

    override suspend fun updateMessagesStatus(
        status: MessageEntity.Status,
        id: List<String>,
        conversationId: QualifiedIDEntity
    ) = withContext(coroutineContext) {
        queries.transaction {
            id.forEach { queries.updateMessageStatus(status, it, conversationId) }
        }
    }

    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): MessageEntity? = withContext(coroutineContext) {
        queries.selectById(id, conversationId, mapper::toEntityMessageFromView).executeAsOneOrNull()
    }

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

    override suspend fun getNotificationMessage(): Flow<List<NotificationMessageEntity>> =
        notificationQueries.getNotificationsMessages(mapper::toNotificationEntity)
            .asFlow()
            .flowOn(coroutineContext)
            .mapToList()
            .distinctUntilChanged()

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
            val selfMention = newTextContent.mentions.firstNotNullOfOrNull { it.userId == selfUserId }
            if (selfMention != null) {
                unreadEventsQueries.updateEvent(UnreadEventTypeEntity.MENTION, currentMessageId, conversationId)
            } else {
                unreadEventsQueries.updateEvent(UnreadEventTypeEntity.MESSAGE, currentMessageId, conversationId)
            }

            queries.updateMessageId(newMessageId, currentMessageId, conversationId)
            queries.updateQuotedMessageId(newMessageId, currentMessageId, conversationId)
        }
    }

    override suspend fun observeLastMessages(): Flow<List<MessagePreviewEntity>> =
        messagePreviewQueries.getLastMessages(mapper::toPreviewEntity).asFlow().flowOn(coroutineContext).mapToList()

    override suspend fun observeConversationsUnreadEvents(): Flow<List<ConversationUnreadEventEntity>> {
        return unreadEventsQueries.getConversationsUnreadEvents(unreadEventMapper::toConversationUnreadEntity)
            .asFlow().mapToList()
    }

    override suspend fun observeUnreadEvents(): Flow<Map<ConversationIDEntity, List<UnreadEventEntity>>> =
        unreadEventsQueries.getUnreadEvents(unreadEventMapper::toUnreadEntity).asFlow().mapToList()
            .map { it.groupBy { event -> event.conversationId } }

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

    override suspend fun promoteMessageToSentUpdatingServerTime(
        conversationId: ConversationIDEntity,
        messageUuid: String,
        serverDate: Instant?,
        millis: Long
    ) = withContext(coroutineContext) {
        queries.promoteMessageToSentUpdatingServerTime(
            server_creation_date = serverDate,
            conversation_id = conversationId,
            message_id = messageUuid,
            delivery_duration = Instant.fromEpochMilliseconds(millis)
        )
    }

    override suspend fun getEphemeralMessagesMarkedForDeletion(): List<MessageEntity> {
        return withContext(coroutineContext) {
            queries.selectAllEphemeralMessagesMarkedForDeletion(mapper::toEntityMessageFromView).executeAsList()
        }
    }

    override suspend fun updateSelfDeletionStartDate(conversationId: QualifiedIDEntity, messageId: String, selfDeletionStartDate: Instant) {
        return withContext(coroutineContext) {
            queries.markSelfDeletionStartDate(selfDeletionStartDate, conversationId, messageId)
        }
    }

    override suspend fun insertFailedRecipientDelivery(
        id: String,
        conversationsId: QualifiedIDEntity,
        recipientsFailed: List<QualifiedIDEntity>,
        recipientFailureTypeEntity: RecipientFailureTypeEntity
    ) = withContext(coroutineContext) {
        queries.insertMessageRecipientsFailure(id, conversationsId, recipientsFailed, recipientFailureTypeEntity)
    }

    override suspend fun moveMessages(from: ConversationIDEntity, to: ConversationIDEntity) =
        withContext(coroutineContext) {
            queries.moveMessages(to, from)
        }

    override suspend fun getConversationUnreadEventsCount(conversationId: QualifiedIDEntity): Long = withContext(coroutineContext) {
        unreadEventsQueries.getConversationUnreadEventsCount(conversationId).executeAsOne()
    }

    override suspend fun observeMessageVisibility(
        messageUuid: String,
        conversationId: QualifiedIDEntity
    ): Flow<MessageEntity.Visibility?> {
        return queries.selectMessageVisibility(messageUuid, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .distinctUntilChanged()
    }

    override suspend fun getConversationMessagesFromSearch(
        searchQuery: String,
        conversationId: QualifiedIDEntity
    ): List<MessageEntity> = withContext(coroutineContext) {
        queries.selectConversationMessagesFromSearch(
            searchQuery,
            conversationId,
            mapper::toEntityMessageFromView
        ).executeAsList()
    }

    override suspend fun getSearchedConversationMessagePosition(
        conversationId: QualifiedIDEntity,
        messageId: String
    ): Int = withContext(coroutineContext) {
        queries
            .selectSearchedConversationMessagePosition(conversationId, messageId)
            .executeAsOne()
            .toInt()
    }

    override val platformExtensions: MessageExtensions = MessageExtensionsImpl(queries, mapper, coroutineContext)

}
