package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.Query
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.ASSET
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.MEMBER_CHANGE
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.TEXT
import com.wire.kalium.persistence.dao.message.MessageEntity.ContentType.UNKNOWN
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import com.wire.kalium.persistence.Message as SQLDelightMessage
import com.wire.kalium.persistence.MessageAssetContent as SQLDelightMessageAssetContent
import com.wire.kalium.persistence.MessageMemberChangeContent as SQLDelightMessageMemberChangeContent
import com.wire.kalium.persistence.MessageTextContent as SQLDelightMessageTextContent
import com.wire.kalium.persistence.MessageUnknownContent as SQLDelightMessageUnknownContent

class MessageMapper {
    fun toModel(msg: SQLDelightMessage, content: MessageEntityContent): MessageEntity = when (content) {
        is MessageEntityContent.Regular -> MessageEntity.Regular(
            content = content,
            id = msg.id,
            conversationId = msg.conversation_id,
            date = msg.date,
            senderUserId = msg.sender_user_id,
            senderClientId = msg.sender_client_id!!,
            status = msg.status,
            editStatus = mapEditStatus(msg.last_edit_timestamp),
            visibility = msg.visibility
        )
        is MessageEntityContent.System -> MessageEntity.System(
            content = content,
            id = msg.id,
            conversationId = msg.conversation_id,
            date = msg.date,
            senderUserId = msg.sender_user_id,
            status = msg.status,
            visibility = msg.visibility
        )
    }

    fun toModel(content: SQLDelightMessageTextContent) = MessageEntityContent.Text(content.text_body ?: "")

    fun toModel(content: SQLDelightMessageAssetContent) = MessageEntityContent.Asset(
        assetSizeInBytes = content.asset_size,
        assetName = content.asset_name,
        assetMimeType = content.asset_mime_type,
        assetDownloadStatus = content.asset_download_status,
        assetOtrKey = content.asset_otr_key,
        assetSha256Key = content.asset_sha256,
        assetId = content.asset_id,
        assetToken = content.asset_token,
        assetDomain = content.asset_domain,
        assetEncryptionAlgorithm = content.asset_encryption_algorithm,
        assetWidth = content.asset_width,
        assetHeight = content.asset_height,
        assetDurationMs = content.asset_duration_ms,
        assetNormalizedLoudness = content.asset_normalized_loudness,
    )

    fun toModel(content: SQLDelightMessageMemberChangeContent) = MessageEntityContent.MemberChange(
        memberUserIdList = content.member_change_list,
        memberChangeType = content.member_change_type
    )

    fun toModel(content: SQLDelightMessageUnknownContent) = MessageEntityContent.Unknown(
        typeName = content.unknown_type_name,
        encodedData = content.unknown_encoded_data
    )

    private fun mapEditStatus(lastEditTimestamp: String?) =
        lastEditTimestamp?.let { MessageEntity.EditStatus.Edited(it) }
            ?: MessageEntity.EditStatus.NotEdited
}

class MessageDAOImpl(private val queries: MessagesQueries) : MessageDAO {
    private val mapper = MessageMapper()

    override suspend fun deleteMessage(id: String, conversationsId: QualifiedIDEntity) = queries.deleteMessage(id, conversationsId)

    override suspend fun markMessageAsDeleted(id: String, conversationsId: QualifiedIDEntity) =
        queries.markMessageAsDeleted(id, conversationsId)

    override suspend fun markAsEdited(editTimeStamp: String, conversationId: QualifiedIDEntity, id: String) {
        queries.markMessageAsEdited(editTimeStamp, id, conversationId)
    }

    override suspend fun deleteAllMessages() = queries.deleteAllMessages()

    override suspend fun insertMessage(message: MessageEntity) = insertInDB(message)

    override suspend fun insertMessages(messages: List<MessageEntity>) =
        queries.transaction {
            messages.forEach { insertInDB(it) }
        }

    @Suppress("ComplexMethod")
    private fun insertInDB(message: MessageEntity) {
        queries.transaction {
            queries.insertMessage(
                id = message.id,
                conversation_id = message.conversationId,
                date = message.date,
                sender_user_id = message.senderUserId,
                sender_client_id = if (message is MessageEntity.Regular) message.senderClientId else null,
                visibility = message.visibility,
                status = message.status,
                content_type = contentTypeOf(message.content)
            )
            when (val content = message.content) {
                is MessageEntityContent.Text -> queries.insertMessageTextContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    text_body = content.messageBody
                )
                is MessageEntityContent.Asset -> queries.insertMessageAssetContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    asset_size = content.assetSizeInBytes,
                    asset_name = content.assetName,
                    asset_mime_type = content.assetMimeType,
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
                is MessageEntityContent.Unknown -> queries.insertMessageUnknownContent(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    unknown_encoded_data = content.encodedData,
                    unknown_type_name = content.typeName
                )
                is MessageEntityContent.MemberChange -> queries.insertMemberChangeMessage(
                    message_id = message.id,
                    conversation_id = message.conversationId,
                    member_change_list = content.memberUserIdList,
                    member_change_type = content.memberChangeType
                )
            }
        }
    }

    override suspend fun updateAssetDownloadStatus(
        downloadStatus: MessageEntity.DownloadStatus,
        id: String,
        conversationId: QualifiedIDEntity
    ) = queries.updateAssetDownloadStatus(downloadStatus, id, conversationId)

    override suspend fun updateMessageStatus(status: MessageEntity.Status, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageStatus(status, id, conversationId)

    override suspend fun updateMessageId(conversationId: QualifiedIDEntity, oldMesageId: String, newMessageId: String) {
        queries.updateMessageId(newMessageId, oldMesageId, conversationId)
    }

    override suspend fun updateMessageDate(date: String, id: String, conversationId: QualifiedIDEntity) =
        queries.updateMessageDate(date, id, conversationId)

    override suspend fun updateMessagesAddMillisToDate(millis: Long, conversationId: QualifiedIDEntity, status: MessageEntity.Status) =
        queries.updateMessagesAddMillisToDate(millis, conversationId, status)

    override suspend fun getMessagesFromAllConversations(limit: Int, offset: Int): Flow<List<MessageEntity>> =
        queries.selectAllMessages(limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun getMessageById(id: String, conversationId: QualifiedIDEntity): Flow<MessageEntity?> =
        queries.selectById(id, conversationId)
            .asFlow()
            .mapToOneOrNull()
            .flatMapLatest { it?.toMessageEntityFlow() ?: flowOf(null) }

    override suspend fun getMessagesByConversationAndVisibility(
        conversationId: QualifiedIDEntity,
        limit: Int,
        offset: Int,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>> =
        queries.selectByConversationIdAndVisibility(conversationId, visibility, limit.toLong(), offset.toLong())
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    override suspend fun getMessagesByConversationAndVisibilityAfterDate(
        conversationId: QualifiedIDEntity,
        date: String,
        visibility: List<MessageEntity.Visibility>
    ): Flow<List<MessageEntity>>  =
        queries.selectMessagesByConversationIdAndVisibilityAfterDate(conversationId, visibility, date)
            .asFlow()
            .mapToList()
            .toMessageEntityListFlow()

    override suspend fun getAllPendingMessagesFromUser(userId: UserIDEntity): List<MessageEntity> =
        queries.selectMessagesFromUserByStatus(userId, MessageEntity.Status.PENDING)
            .executeAsList()
            .map { it.toMessageEntity() }

    override suspend fun updateTextMessageContent(
        conversationId: QualifiedIDEntity,
        messageId: String,
        newTextContent: MessageEntityContent.Text
    ) {
        queries.updateMessageTextContent(newTextContent.messageBody, messageId, conversationId)
    }

    private fun contentTypeOf(content: MessageEntityContent): MessageEntity.ContentType = when (content) {
        is MessageEntityContent.Text -> TEXT
        is MessageEntityContent.Asset -> ASSET
        is MessageEntityContent.MemberChange -> MEMBER_CHANGE
        is MessageEntityContent.Unknown -> UNKNOWN
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun Flow<List<SQLDelightMessage>>.toMessageEntityListFlow(): Flow<List<MessageEntity>> = this.flatMapLatest {
        if (it.isEmpty()) flowOf(listOf())
        else combine(it.map { message -> message.toMessageEntityFlow() }) { it.asList() }
    }

    private fun SQLDelightMessage.toMessageEntityFlow() = when (this.content_type) {
        TEXT -> this.queryOneOrDefaultFlow(queries::selectMessageTextContent, mapper::toModel)
        ASSET -> this.queryOneOrDefaultFlow(queries::selectMessageAssetContent, mapper::toModel)
        MEMBER_CHANGE -> this.queryOneOrDefaultFlow(queries::selectMessageMemberChangeContent, mapper::toModel)
        UNKNOWN -> this.queryOneOrDefaultFlow(queries::selectMessageUnknownContent, mapper::toModel)
    }.map { mapper.toModel(this, it) }

    private fun SQLDelightMessage.toMessageEntity() = when (this.content_type) {
        TEXT -> this.queryOneOrDefault(queries::selectMessageTextContent, mapper::toModel)
        ASSET -> this.queryOneOrDefault(queries::selectMessageAssetContent, mapper::toModel)
        MEMBER_CHANGE -> this.queryOneOrDefault(queries::selectMessageMemberChangeContent, mapper::toModel)
        UNKNOWN -> this.queryOneOrDefault(queries::selectMessageUnknownContent, mapper::toModel)
    }.let { mapper.toModel(this, it) }

    private val defaultMessageEntityContent = MessageEntityContent.Text("")

    private fun <T : Any> SQLDelightMessage.queryOneOrDefault(
        query: (String, QualifiedIDEntity) -> Query<T>,
        mapper: (T) -> MessageEntityContent,
        default: MessageEntityContent = defaultMessageEntityContent,
    ): MessageEntityContent =
        query(this.id, this.conversation_id).executeAsOneOrNull()?.let(mapper) ?: default

    private fun <T : Any> SQLDelightMessage.queryOneOrDefaultFlow(
        query: (String, QualifiedIDEntity) -> Query<T>,
        mapper: (T) -> MessageEntityContent,
        default: MessageEntityContent = defaultMessageEntityContent,
    ): Flow<MessageEntityContent> =
        query(this.id, this.conversation_id).asFlow().mapToOneOrNull().map { it?.let(mapper) ?: default }
}
