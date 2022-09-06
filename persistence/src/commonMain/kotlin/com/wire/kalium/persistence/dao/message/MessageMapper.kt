package com.wire.kalium.persistence.dao.message

import app.cash.sqldelight.Query
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.squareup.sqldelight.runtime.coroutines.mapToOneOrNull
import com.wire.kalium.persistence.Message
import com.wire.kalium.persistence.MessageAssetContent
import com.wire.kalium.persistence.MessageFailedToDecryptContent
import com.wire.kalium.persistence.MessageMemberChangeContent
import com.wire.kalium.persistence.MessageMention
import com.wire.kalium.persistence.MessageMissedCallContent
import com.wire.kalium.persistence.MessageRestrictedAssetContent
import com.wire.kalium.persistence.MessageTextContent
import com.wire.kalium.persistence.MessageUnknownContent
import com.wire.kalium.persistence.MessagesQueries
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MessageMapper(private val queries: MessagesQueries) {

    private val defaultMessageEntityContent = MessageEntityContent.Text("")

    fun toModel(msg: Message, content: MessageEntityContent): MessageEntity = when (content) {
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

    fun toModel(content: MessageTextContent, mentions: List<MessageMention>) = MessageEntityContent.Text(
        messageBody = content.text_body ?: "",
        mentions = mentions.map {
            MessageEntity.Mention(
                start = it.start,
                length = it.length,
                userId = it.user_id
            )
        }
    )

    fun toModel(content: MessageRestrictedAssetContent) = MessageEntityContent.RestrictedAsset(
        content.asset_mime_type, content.asset_size, content.asset_name
    )

    fun toModel(content: MessageAssetContent) = MessageEntityContent.Asset(
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

    fun toModel(content: MessageMemberChangeContent) = MessageEntityContent.MemberChange(
        memberUserIdList = content.member_change_list,
        memberChangeType = content.member_change_type
    )

    fun toModel(content: MessageUnknownContent) = MessageEntityContent.Unknown(
        typeName = content.unknown_type_name,
        encodedData = content.unknown_encoded_data
    )

    fun toModel(content: MessageFailedToDecryptContent) = MessageEntityContent.FailedDecryption(
        encodedData = content.unknown_encoded_data
    )

    fun toModel(content: MessageMissedCallContent) = MessageEntityContent.MissedCall

    private fun mapEditStatus(lastEditTimestamp: String?) =
        lastEditTimestamp?.let { MessageEntity.EditStatus.Edited(it) }
            ?: MessageEntity.EditStatus.NotEdited


    private fun <T : Any> Message.queryOneOrDefault(
        query: (String, QualifiedIDEntity) -> Query<T>,
        mapper: (T) -> MessageEntityContent,
        default: MessageEntityContent = defaultMessageEntityContent,
    ): MessageEntityContent =
        query(this.id, this.conversation_id).executeAsOneOrNull()?.let(mapper) ?: default

    private fun <T : Any> Message.queryOneOrDefaultFlow(
        query: (String, QualifiedIDEntity) -> Query<T>,
        mapper: (T) -> MessageEntityContent,
        default: MessageEntityContent = defaultMessageEntityContent,
    ): Flow<MessageEntityContent> =
        query(this.id, this.conversation_id).asFlow().mapToOneOrNull().map { it?.let(mapper) ?: default }


    fun toMessageEntityFlow(message: Message) = message.run {
        when (this.content_type) {
            MessageEntity.ContentType.TEXT -> queries.selectMessageTextContent(this.id, this.conversation_id).asFlow().mapToOneOrNull()
                .combine(queries.selectMessageMentions(this.id, this.conversation_id).asFlow().mapToList()) { content, mentions ->
                    content?.let { toModel(content, mentions) } ?: defaultMessageEntityContent
                }

            MessageEntity.ContentType.ASSET -> this.queryOneOrDefaultFlow(queries::selectMessageAssetContent, ::toModel)
            MessageEntity.ContentType.KNOCK -> flowOf(MessageEntityContent.Knock(false))
            MessageEntity.ContentType.MEMBER_CHANGE -> this.queryOneOrDefaultFlow(queries::selectMessageMemberChangeContent, ::toModel)
            MessageEntity.ContentType.MISSED_CALL -> this.queryOneOrDefaultFlow(queries::selectMessageMissedCallContent, ::toModel)
            MessageEntity.ContentType.UNKNOWN -> this.queryOneOrDefaultFlow(queries::selectMessageUnknownContent, ::toModel)
            MessageEntity.ContentType.FAILED_DECRYPTION -> this.queryOneOrDefaultFlow(
                queries::selectFailedDecryptionMessageContent,
                ::toModel
            )

            MessageEntity.ContentType.RESTRICTED_ASSET -> this.queryOneOrDefaultFlow(
                queries::selectMessageRestrictedAssetContent,
                ::toModel
            )
        }.map { toModel(this, it) }
    }

    fun toMessageEntity(message: Message) = message.run {
        when (this.content_type) {
            MessageEntity.ContentType.TEXT -> queries.selectMessageTextContent(this.id, this.conversation_id).executeAsOneOrNull()
                .let { it to queries.selectMessageMentions(this.id, this.conversation_id).executeAsList() }
                .let { (content, mentions) -> content?.let { toModel(content, mentions) } ?: defaultMessageEntityContent }

            MessageEntity.ContentType.ASSET -> this.queryOneOrDefault(queries::selectMessageAssetContent, ::toModel)
            MessageEntity.ContentType.KNOCK -> MessageEntityContent.Knock(false)
            MessageEntity.ContentType.MEMBER_CHANGE -> this.queryOneOrDefault(queries::selectMessageMemberChangeContent, ::toModel)
            MessageEntity.ContentType.MISSED_CALL -> this.queryOneOrDefault(queries::selectMessageMissedCallContent, ::toModel)
            MessageEntity.ContentType.UNKNOWN -> this.queryOneOrDefault(queries::selectMessageUnknownContent, ::toModel)
            MessageEntity.ContentType.FAILED_DECRYPTION -> this.queryOneOrDefault(queries::selectFailedDecryptionMessageContent, ::toModel)
            MessageEntity.ContentType.RESTRICTED_ASSET -> this.queryOneOrDefault(queries::selectMessageRestrictedAssetContent, ::toModel)
        }.let { toModel(this, it) }
    }
}
