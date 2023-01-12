package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.coroutines.asFlow
import com.wire.kalium.persistence.ConversationsQueries
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.ReactionsQueries
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.CONVERSATION_RENAMED
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.CRYPTO_SESSION_RESET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.FAILED_DECRYPTION
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.KNOCK
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.MEMBER_CHANGE
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.MISSED_CALL
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.REMOVED_FROM_TEAM
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.RESTRICTED_ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.UNKNOWN
import com.wire.kalium.persistence.kaliumLogger
import com.wire.kalium.persistence.util.mapToList
import com.wire.kalium.persistence.util.mapToOneOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

@Suppress("TooManyFunctions")
class MessageDAOImpl(
    private val queries: MessagesQueries,
    private val conversationsQueries: ConversationsQueries,
    private val selfUserId: UserIDEntity,
    private val reactionsQueries: ReactionsQueries,
    private val coroutineContext: CoroutineContext
) : MessageDAO {
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
            if (updateConversationReadDate) {
                conversationsQueries.updateConversationReadDate(message.date, message.conversationId)
            }

            insertInDB(message)

            if (!nonSuspendNeedsToBeNotified(message.id, message.conversationId)) {
                conversationsQueries.updateConversationNotificationsDate(message.date, message.conversationId)
            }

            if (updateConversationModifiedDate) {
                conversationsQueries.updateConversationModifiedDate(message.date, message.conversationId)
            }
        }
    }

    override suspend fun getLatestMessageFromOtherUsers(): MessageEntity? = withContext(coroutineContext) {
        queries.getLatestMessageFromOtherUsers(mapper::toEntityMessageFromView).executeAsOneOrNull()
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
                insertBaseMessage(message)
                insertMessageContent(message)
            }
        }
    }

    private fun insertBaseMessage(message: MessageEntity) {
        // do not add withContext
        queries.insertOrIgnoreMessage(
            id = message.id,
            conversation_id = message.conversationId,
            date = message.date,
            sender_user_id = message.senderUserId,
            sender_client_id = if (message is MessageEntity.Regular) message.senderClientId else null,
            visibility = message.visibility,
            status = message.status,
            content_type = contentTypeOf(message.content),
            expects_read_confirmation = if (message is MessageEntity.Regular) message.expectsReadConfirmation else false
        )
    }

    @Suppress("LongMethod")
    private fun insertMessageContent(message: MessageEntity) {
        when (val content = message.content) {
            is MessageEntityContent.Text -> {
                queries.insertMessageTextContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    text_body = content.messageBody,
                    quoted_message_id = content.quotedMessageId,
                    is_quote_verified = content.isQuoteVerified
                )
                content.mentions.forEach {
                    queries.insertMessageMention(
                        message_id = message.id,
                        conversation_id = message.conversationId,
                        start = it.start,
                        length = it.length,
                        user_id = it.userId
                    )
                }
            }

            is MessageEntityContent.RestrictedAsset -> queries.insertMessageRestrictedAssetContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                asset_mime_type = content.mimeType,
                asset_size = content.assetSizeInBytes,
                asset_name = content.assetName
            )

            is MessageEntityContent.Asset -> {
                queries.insertMessageAssetContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    asset_size = content.assetSizeInBytes,
                    asset_name = content.assetName,
                    asset_mime_type = content.assetMimeType,
                    asset_upload_status = content.assetUploadStatus,
                    asset_download_status = content.assetDownloadStatus,
                    asset_otr_key = content.assetOtrKey,
                    asset_sha256 = content.assetSha256Key,
                    asset_id = content.assetId,
                    asset_token = content.assetToken,
                    asset_domain = content.assetDomain,
                    asset_encryption_algorithm = content.assetEncryptionAlgorithm,
                    asset_width = content.assetWidth,
                    asset_height = content.assetHeight,
                    asset_duration_ms = content.assetDurationMs,
                    asset_normalized_loudness = content.assetNormalizedLoudness
                )
            }

            is MessageEntityContent.Unknown -> queries.insertMessageUnknownContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
                unknown_type_name = content.typeName
            )

            is MessageEntityContent.FailedDecryption -> queries.insertFailedDecryptionMessageContent(
                message_id = message.id,
                conversation_id = message.conversationId,
                unknown_encoded_data = content.encodedData,
            )

            is MessageEntityContent.MemberChange -> queries.insertMemberChangeMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                member_change_list = content.memberUserIdList,
                member_change_type = content.memberChangeType
            )

            is MessageEntityContent.MissedCall -> queries.insertMissedCallMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                caller_id = message.senderUserId
            )

            is MessageEntityContent.Knock -> {
                /** NO-OP. No need to insert any content for Knock messages */
            }

            is MessageEntityContent.ConversationRenamed -> queries.insertConversationRenamedMessage(
                message_id = message.id,
                conversation_id = message.conversationId,
                conversation_name = content.conversationName
            )

            is MessageEntityContent.TeamMemberRemoved -> {
                // TODO: What needs to be done here?
                //       When migrating to Kotlin 1.7, when branches must be exhaustive!
                kaliumLogger.w("TeamMemberRemoved message insertion not handled")
            }

            is MessageEntityContent.CryptoSessionReset -> {
                // NOTHING TO DO
            }
        }
    }

    /**
     * Returns true if the [message] is an asset message that is already in the DB and any of its decryption keys are null/empty. This means
     * that this asset message that is in the DB was only a preview message with valid metadata but no valid keys (Web clients send 2
     * separated messages). Therefore, it still needs to be updated with the valid keys in order to be displayed.
     */
    private fun isValidAssetMessageUpdate(message: MessageEntity): Boolean {
        if (message !is MessageEntity.Regular) return false
        // If asset has no valid keys, no need to query the DB
        val hasValidKeys = message.content is MessageEntityContent.Asset && message.content.hasValidRemoteData()
        val currentMessageHasMissingAssetInformation =
            hasValidKeys && queries.selectById(message.id, message.conversationId).executeAsList().firstOrNull()?.let {
                val isFromSameSender = message.senderUserId == it.senderUserId
                        && message.senderClientId == it.senderClientId
                (it.assetId.isNullOrEmpty() || it.assetOtrKey.isNullOrEmpty() || it.assetSha256.isNullOrEmpty()) && isFromSameSender
            } ?: false
        return currentMessageHasMissingAssetInformation
    }

    private fun updateAssetMessage(message: MessageEntity) {
        if (message.content !is MessageEntityContent.Asset) {
            kaliumLogger.e("The message can't be updated because it is not an asset")
            return
        }
        val assetMessageContent = message.content as MessageEntityContent.Asset
        with(assetMessageContent) {
            // This will ONLY update the VISIBILITY of the original base message and all the asset content related fields
            queries.updateAssetContent(
                messageId = message.id,
                conversationId = message.conversationId,
                visibility = message.visibility,
                assetId = assetId,
                assetDomain = assetDomain,
                assetToken = assetToken,
                assetSize = assetSizeInBytes,
                assetMimeType = assetMimeType,
                assetName = assetName,
                assetOtrKey = assetOtrKey,
                assetSha256 = assetSha256Key,
                assetUploadStatus = assetUploadStatus,
                assetDownloadStatus = assetDownloadStatus,
                assetEncryptionAlgorithm = assetEncryptionAlgorithm
            )
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
            queries.updateMessageDate(date, id, conversationId)
        }

    override suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status) =
        withContext(coroutineContext) {
            queries.updateMessagesAddMillisToDate(millis, conversationId, status)
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
            conversationId, visibility, date,
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
            queries.markMessageAsEdited(editTimeStamp, currentMessageId, conversationId)
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
        withContext(coroutineContext) {
            queries.getLastMessages(mapper::toPreviewEntity).asFlow().mapToList()
        }

    override suspend fun observeUnreadMessages(): Flow<List<MessagePreviewEntity>> = withContext(coroutineContext) {
        queries.getUnreadMessages(mapper::toPreviewEntity).asFlow().mapToList()
    }

    private fun contentTypeOf(content: MessageEntityContent): MessageEntity.ContentType = when (content) {
        is MessageEntityContent.Text -> TEXT
        is MessageEntityContent.Asset -> ASSET
        is MessageEntityContent.Knock -> KNOCK
        is MessageEntityContent.MemberChange -> MEMBER_CHANGE
        is MessageEntityContent.MissedCall -> MISSED_CALL
        is MessageEntityContent.Unknown -> UNKNOWN
        is MessageEntityContent.FailedDecryption -> FAILED_DECRYPTION
        is MessageEntityContent.RestrictedAsset -> RESTRICTED_ASSET
        is MessageEntityContent.ConversationRenamed -> CONVERSATION_RENAMED
        is MessageEntityContent.TeamMemberRemoved -> REMOVED_FROM_TEAM
        is MessageEntityContent.CryptoSessionReset -> CRYPTO_SESSION_RESET
    }

    override suspend fun resetAssetDownloadStatus() = withContext(coroutineContext) {
        queries.resetAssetDownloadStatus()
    }

    override suspend fun markMessagesAsDecryptionResolved(
        conversationId: QualifiedIDEntity,
        userId: QualifiedIDEntity,
        clientId: String,
    ) = withContext(coroutineContext) {
        // TODO: mark all messages form the user client as resolved regardless of the conversation
        queries.transaction {
            val messages =
                queries.selectFailedDecryptedByConversationIdAndSenderIdAndClientId(conversationId, userId, clientId).executeAsList()
            queries.markMessagesAsDecryptionResolved(messages)
        }
    }

    override suspend fun resetAssetUploadStatus() = withContext(coroutineContext) {
        queries.resetAssetUploadStatus()
    }

    override suspend fun getPendingToConfirmMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility>
    ): List<MessageEntity> = withContext(coroutineContext) {
        queries
            .selectPendingMessagesByConversationIdAndVisibilityAfterDate(conversationId, visibility, date, mapper::toEntityMessageFromView)
            .executeAsList()
    }

    override suspend fun getReceiptModeFromGroupConversationByQualifiedID(qualifiedID: QualifiedIDEntity): ConversationEntity.ReceiptMode? =
        withContext(coroutineContext) {
            conversationsQueries.selectReceiptModeFromGroupConversationByQualifiedId(qualifiedID)
                .executeAsOneOrNull()
        }

    override val platformExtensions: MessageExtensions = MessageExtensionsImpl(queries, mapper, coroutineContext)

    private fun MessageEntityContent.Asset.hasValidRemoteData() =
        assetId.isNotEmpty() && assetOtrKey.isNotEmpty() && assetSha256Key.isNotEmpty()
}

internal fun ByteArray?.isNullOrEmpty() = this?.isEmpty() ?: true
