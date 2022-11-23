package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.MemberMapper
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.datetime.Instant

interface MessageMapper {
    fun fromMessageToEntity(message: Message): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message
    fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage
}

class MessageMapperImpl(
    private val idMapper: IdMapper,
    private val memberMapper: MemberMapper,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val selfUserId: UserId,
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId)
) : MessageMapper {

    override fun fromMessageToEntity(message: Message): MessageEntity {
        val status = when (message.status) {
            Message.Status.PENDING -> MessageEntity.Status.PENDING
            Message.Status.SENT -> MessageEntity.Status.SENT
            Message.Status.READ -> MessageEntity.Status.READ
            Message.Status.FAILED -> MessageEntity.Status.FAILED
        }
        val visibility = message.visibility.toEntityVisibility()
        return when (message) {
            is Message.Regular -> MessageEntity.Regular(
                id = message.id,
                content = message.content.toMessageEntityContent(),
                conversationId = idMapper.toDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.toDaoModel(message.senderUserId),
                senderClientId = message.senderClientId.value,
                status = status,
                editStatus = when (message.editStatus) {
                    is Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
                    is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(message.editStatus.lastTimeStamp)
                },
                visibility = visibility
            )

            is Message.System -> MessageEntity.System(
                id = message.id,
                content = message.content.toMessageEntityContent(),
                conversationId = idMapper.toDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.toDaoModel(message.senderUserId),
                status = status,
                visibility = visibility
            )
        }
    }

    override fun fromEntityToMessage(message: MessageEntity): Message {
        val status = when (message.status) {
            MessageEntity.Status.PENDING -> Message.Status.PENDING
            MessageEntity.Status.SENT -> Message.Status.SENT
            MessageEntity.Status.READ -> Message.Status.READ
            MessageEntity.Status.FAILED -> Message.Status.FAILED
        }
        val visibility = when (message.visibility) {
            MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
            MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
            MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
        }
        return when (message) {
            is MessageEntity.Regular -> Message.Regular(
                id = message.id,
                content = message.content.toMessageContent(visibility == Message.Visibility.HIDDEN),
                conversationId = idMapper.fromDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.fromDaoModel(message.senderUserId),
                senderClientId = ClientId(message.senderClientId),
                status = status,
                editStatus = when (val editStatus = message.editStatus) {
                    MessageEntity.EditStatus.NotEdited -> Message.EditStatus.NotEdited
                    is MessageEntity.EditStatus.Edited -> Message.EditStatus.Edited(editStatus.lastTimeStamp)
                },
                visibility = visibility,
                reactions = Message.Reactions(message.reactions.totalReactions, message.reactions.selfUserReactions)
            )

            is MessageEntity.System -> Message.System(
                id = message.id,
                content = message.content.toMessageContent(),
                conversationId = idMapper.fromDaoModel(message.conversationId),
                date = message.date,
                senderUserId = idMapper.fromDaoModel(message.senderUserId),
                status = status,
                visibility = visibility
            )
        }
    }

    override fun fromMessageToLocalNotificationMessage(message: Message, author: LocalNotificationMessageAuthor): LocalNotificationMessage =
        when (val content = message.content) {
            is MessageContent.Text -> LocalNotificationMessage.Text(author, message.date, content.value)
            // TODO(notifications): Handle other message types
            is MessageContent.Asset -> {
                val type = if (content.value.metadata is Image) LocalNotificationCommentType.PICTURE
                else LocalNotificationCommentType.FILE

                LocalNotificationMessage.Comment(author, message.date, type)
            }

            is MessageContent.MissedCall ->
                LocalNotificationMessage.Comment(author, message.date, LocalNotificationCommentType.MISSED_CALL)

            else -> LocalNotificationMessage.Comment(author, message.date, LocalNotificationCommentType.NOT_SUPPORTED_YET)
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.Regular.toMessageEntityContent(): MessageEntityContent.Regular = when (this) {
        is MessageContent.Text -> MessageEntityContent.Text(
            messageBody = this.value,
            mentions = this.mentions.map { messageMentionMapper.fromModelToDao(it) },
            quotedMessageId = this.quotedMessageReference?.quotedMessageId,
            isQuoteVerified = this.quotedMessageReference?.isVerified
        )

        is MessageContent.Asset -> with(this.value) {
            val assetWidth = when (metadata) {
                is Image -> metadata.width
                is Video -> metadata.width
                else -> null
            }
            val assetHeight = when (metadata) {
                is Image -> metadata.height
                is Video -> metadata.height
                else -> null
            }
            val assetDurationMs = when (metadata) {
                is Video -> metadata.durationMs
                is Audio -> metadata.durationMs
                else -> null
            }
            MessageEntityContent.Asset(
                assetSizeInBytes = sizeInBytes,
                assetName = name,
                assetMimeType = mimeType,
                assetUploadStatus = assetMapper.fromUploadStatusToDaoModel(uploadStatus),
                assetDownloadStatus = assetMapper.fromDownloadStatusToDaoModel(downloadStatus),
                assetOtrKey = remoteData.otrKey,
                assetSha256Key = remoteData.sha256,
                assetId = remoteData.assetId,
                assetDomain = remoteData.assetDomain,
                assetToken = remoteData.assetToken,
                assetEncryptionAlgorithm = remoteData.encryptionAlgorithm?.name,
                assetWidth = assetWidth,
                assetHeight = assetHeight,
                assetDurationMs = assetDurationMs,
                assetNormalizedLoudness = if (metadata is Audio) metadata.normalizedLoudness else null
            )
        }

        is MessageContent.RestrictedAsset -> MessageEntityContent.RestrictedAsset(this.mimeType, this.sizeInBytes, this.name)

        // We store the encoded data in case we decide to try to decrypt them again in the future
        is MessageContent.FailedDecryption -> MessageEntityContent.FailedDecryption(this.encodedData)

        // We store the unknown fields of the message in case we want to start handling them in the future
        is MessageContent.Unknown -> MessageEntityContent.Unknown(this.typeName, this.encodedData)

        // We don't care about the content of these messages as they are only used to perform other actions, i.e. update the content of a
        // previously stored message, delete the content of a previously stored message, etc... Therefore, we map their content to Unknown
        is MessageContent.Calling -> MessageEntityContent.Unknown()
        is MessageContent.DeleteMessage -> MessageEntityContent.Unknown()
        is MessageContent.Reaction -> MessageEntityContent.Unknown()
        is MessageContent.TextEdited -> MessageEntityContent.Unknown()
        is MessageContent.DeleteForMe -> MessageEntityContent.Unknown()
        is MessageContent.Knock -> MessageEntityContent.Knock(hotKnock = this.hotKnock)
        is MessageContent.Empty -> MessageEntityContent.Unknown()
        is MessageContent.LastRead -> MessageEntityContent.Unknown()
        is MessageContent.Cleared -> MessageEntityContent.Unknown()
    }

    private fun MessageContent.System.toMessageEntityContent(): MessageEntityContent.System = when (this) {
        is MessageContent.MemberChange -> {
            val memberUserIdList = this.members.map { idMapper.toDaoModel(it) }
            when (this) {
                is MessageContent.MemberChange.Added ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.ADDED)

                is MessageContent.MemberChange.Removed ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED)
            }
        }

        is MessageContent.MissedCall -> MessageEntityContent.MissedCall
        is MessageContent.ConversationRenamed -> MessageEntityContent.ConversationRenamed(conversationName)
        is MessageContent.TeamMemberRemoved -> MessageEntityContent.TeamMemberRemoved(userName)
    }

    private fun MessageEntityContent.Regular.toMessageContent(hidden: Boolean): MessageContent.Regular = when (this) {
        is MessageEntityContent.Text -> {
            val quotedMessageDetails = this.quotedMessage?.let {
                MessageContent.QuotedMessageDetails(
                    senderId = idMapper.fromDaoModel(it.senderId),
                    senderName = it.senderName,
                    isQuotingSelfUser = it.isQuotingSelfUser,
                    isVerified = it.isVerified,
                    messageId = it.id,
                    timeInstant = Instant.parse(it.dateTime),
                    editInstant = it.editTimestamp?.let { editTime -> Instant.parse(editTime) },
                    quotedContent = quotedContentFromEntity(it)
                )
            }
            MessageContent.Text(
                value = this.messageBody,
                mentions = this.mentions.map { messageMentionMapper.fromDaoToModel(it) },
                quotedMessageReference = quotedMessageDetails?.let {
                    MessageContent.QuoteReference(
                        quotedMessageId = it.messageId,
                        quotedMessageSha256 = null,
                        isVerified = it.isVerified
                    )
                },
                quotedMessageDetails = quotedMessageDetails
            )
        }

        is MessageEntityContent.Asset -> MessageContent.Asset(
            MapperProvider.assetMapper().fromAssetEntityToAssetContent(this)
        )

        is MessageEntityContent.Knock -> MessageContent.Knock(this.hotKnock)

        is MessageEntityContent.RestrictedAsset -> MessageContent.RestrictedAsset(
            this.mimeType, this.assetSizeInBytes, this.assetName
        )

        is MessageEntityContent.Unknown -> MessageContent.Unknown(this.typeName, this.encodedData, hidden)
        is MessageEntityContent.FailedDecryption -> MessageContent.FailedDecryption(this.encodedData)
    }

    private fun quotedContentFromEntity(it: MessageEntityContent.Text.QuotedMessage) = when {
        // Prioritise Invalid and Deleted over content types
        !it.isVerified -> MessageContent.QuotedMessageDetails.Invalid
        !it.visibility.isVisible -> MessageContent.QuotedMessageDetails.Deleted
        it.contentType == MessageEntity.ContentType.TEXT -> MessageContent.QuotedMessageDetails.Text(it.textBody!!)
        it.contentType == MessageEntity.ContentType.ASSET -> {
            MessageContent.QuotedMessageDetails.Asset(
                assetName = it.assetName,
                assetMimeType = requireNotNull(it.assetMimeType)
            )
        }

        // If a new content type can be replied to (Pings, for example), fallback to Invalid
        else -> MessageContent.QuotedMessageDetails.Invalid
    }

    private fun MessageEntityContent.System.toMessageContent(): MessageContent.System = when (this) {
        is MessageEntityContent.MemberChange -> {
            val memberList = this.memberUserIdList.map { idMapper.fromDaoModel(it) }
            when (this.memberChangeType) {
                MessageEntity.MemberChangeType.ADDED -> MessageContent.MemberChange.Added(memberList)
                MessageEntity.MemberChangeType.REMOVED -> MessageContent.MemberChange.Removed(memberList)
            }
        }

        is MessageEntityContent.MissedCall -> MessageContent.MissedCall
        is MessageEntityContent.ConversationRenamed -> MessageContent.ConversationRenamed(conversationName)
        is MessageEntityContent.TeamMemberRemoved -> MessageContent.TeamMemberRemoved(userName)
    }
}

fun Message.Visibility.toEntityVisibility(): MessageEntity.Visibility = when (this) {
    Message.Visibility.VISIBLE -> MessageEntity.Visibility.VISIBLE
    Message.Visibility.HIDDEN -> MessageEntity.Visibility.HIDDEN
    Message.Visibility.DELETED -> MessageEntity.Visibility.DELETED
}
