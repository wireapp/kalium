package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.message.AssetTypeEntity
import com.wire.kalium.persistence.dao.message.AssetTypeEntity.IMAGE
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

interface MessageMapper {
    fun fromMessageToEntity(message: Message.Standalone): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message.Standalone
    fun fromEntityToMessagePreview(message: MessagePreviewEntity): MessagePreview
    fun fromPreviewEntityToUnreadEventCount(message: MessagePreviewEntity): UnreadEventType?
    fun fromMessageToLocalNotificationMessage(message: NotificationMessageEntity): LocalNotificationMessage
}

class MessageMapperImpl(
    private val idMapper: IdMapper,
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val selfUserId: UserId,
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId)
) : MessageMapper {

    override fun fromMessageToEntity(message: Message.Standalone): MessageEntity {
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
                conversationId = message.conversationId.toDao(),
                date = message.date.toInstant(),
                senderUserId = message.senderUserId.toDao(),
                senderClientId = message.senderClientId.value,
                status = status,
                editStatus = when (message.editStatus) {
                    is Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
                    is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(message.editStatus.lastTimeStamp.toInstant())
                },
                visibility = visibility,
                senderName = message.senderUserName,
                isSelfMessage = message.isSelfMessage,
                expectsReadConfirmation = message.expectsReadConfirmation
            )

            is Message.System -> MessageEntity.System(
                id = message.id,
                content = message.content.toMessageEntityContent(),
                conversationId = message.conversationId.toDao(),
                date = message.date.toInstant(),
                senderUserId = message.senderUserId.toDao(),
                status = status,
                visibility = visibility,
                senderName = message.senderUserName,
            )
        }
    }

    override fun fromEntityToMessage(message: MessageEntity): Message.Standalone {
        val status = when (message.status) {
            MessageEntity.Status.PENDING -> Message.Status.PENDING
            MessageEntity.Status.SENT -> Message.Status.SENT
            MessageEntity.Status.READ -> Message.Status.READ
            MessageEntity.Status.FAILED -> Message.Status.FAILED
        }
        val visibility = message.visibility.toModel()

        return when (message) {
            is MessageEntity.Regular -> Message.Regular(
                id = message.id,
                content = message.content.toMessageContent(visibility == Message.Visibility.HIDDEN),
                conversationId = message.conversationId.toModel(),
                date = message.date.toIsoDateTimeString(),
                senderUserId = message.senderUserId.toModel(),
                senderClientId = ClientId(message.senderClientId),
                status = status,
                editStatus = when (val editStatus = message.editStatus) {
                    MessageEntity.EditStatus.NotEdited -> Message.EditStatus.NotEdited
                    is MessageEntity.EditStatus.Edited -> Message.EditStatus.Edited(editStatus.lastDate.toIsoDateTimeString())
                },
                visibility = visibility,
                reactions = Message.Reactions(message.reactions.totalReactions, message.reactions.selfUserReactions),
                senderUserName = message.senderName,
                isSelfMessage = message.isSelfMessage,
                expectsReadConfirmation = message.expectsReadConfirmation
            )

            is MessageEntity.System -> Message.System(
                id = message.id,
                content = message.content.toMessageContent(),
                conversationId = message.conversationId.toModel(),
                date = message.date.toIsoDateTimeString(),
                senderUserId = message.senderUserId.toModel(),
                status = status,
                visibility = visibility,
                senderUserName = message.senderName,
            )
        }
    }

    override fun fromEntityToMessagePreview(message: MessagePreviewEntity): MessagePreview {
        return MessagePreview(
            id = message.id,
            conversationId = message.conversationId.toModel(),
            content = message.content.toMessageContent(),
            date = message.date,
            visibility = message.visibility.toModel(),
            isSelfMessage = message.isSelfMessage
        )
    }

    override fun fromPreviewEntityToUnreadEventCount(message: MessagePreviewEntity): UnreadEventType? {
        return when (message.content) {
            is MessagePreviewEntityContent.Asset -> UnreadEventType.MESSAGE
            is MessagePreviewEntityContent.ConversationNameChange -> null
            is MessagePreviewEntityContent.Knock -> UnreadEventType.KNOCK
            is MessagePreviewEntityContent.MemberChange -> null
            is MessagePreviewEntityContent.MentionedSelf -> UnreadEventType.MENTION
            is MessagePreviewEntityContent.MissedCall -> UnreadEventType.MISSED_CALL
            is MessagePreviewEntityContent.QuotedSelf -> UnreadEventType.REPLY
            is MessagePreviewEntityContent.TeamMemberRemoved -> null
            is MessagePreviewEntityContent.Text -> UnreadEventType.MESSAGE
            is MessagePreviewEntityContent.CryptoSessionReset -> null
            MessagePreviewEntityContent.Unknown -> null
        }
    }

    @Suppress("ComplexMethod")
    override fun fromMessageToLocalNotificationMessage(
        message: NotificationMessageEntity
    ): LocalNotificationMessage =
        when (val content = message.content) {
            is MessagePreviewEntityContent.Text -> LocalNotificationMessage.Text(
                LocalNotificationMessageAuthor(
                    content.senderName ?: "",
                    null
                ), message.date, content.messageBody
            )
            is MessagePreviewEntityContent.Asset -> {
                val type = if (content.type == IMAGE) LocalNotificationCommentType.PICTURE
                else LocalNotificationCommentType.FILE
                LocalNotificationMessage.Comment(LocalNotificationMessageAuthor(content.senderName ?: "", null), message.date, type)
            }

            is MessagePreviewEntityContent.MissedCall ->
                LocalNotificationMessage.Comment(
                    LocalNotificationMessageAuthor(content.senderName ?: "", null),
                    message.date,
                    LocalNotificationCommentType.MISSED_CALL
                )
            is MessagePreviewEntityContent.Knock -> LocalNotificationMessage.Comment(
                LocalNotificationMessageAuthor(content.senderName ?: "", null),
                message.date,
                LocalNotificationCommentType.KNOCK
            )
            is MessagePreviewEntityContent.MentionedSelf -> LocalNotificationMessage.Text(
                author = LocalNotificationMessageAuthor(content.senderName ?: "", null),
                time = message.date,
                text = content.messageBody,
                isMentionedSelf = true,
            )
            is MessagePreviewEntityContent.QuotedSelf -> LocalNotificationMessage.Text(
                author = LocalNotificationMessageAuthor(name = content.senderName ?: "", imageUri = null),
                time = message.date,
                text = content.messageBody,
                isQuotingSelfUser = true
            )
            // TODO(notifications): Handle other message types
            else -> LocalNotificationMessage.Comment(
                LocalNotificationMessageAuthor("", null),
                message.date,
                LocalNotificationCommentType.NOT_SUPPORTED_YET
            )
        }

    @Suppress("ComplexMethod")
    private fun MessageContent.Regular.toMessageEntityContent(): MessageEntityContent.Regular = when (this) {
        is MessageContent.Text -> MessageEntityContent.Text(
            messageBody = this.value,
            mentions = this.mentions.map { messageMentionMapper.fromModelToDao(it) },
            quotedMessageId = this.quotedMessageReference?.quotedMessageId,
            isQuoteVerified = this.quotedMessageReference?.isVerified,
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
                assetNormalizedLoudness = if (metadata is Audio) metadata.normalizedLoudness else null,
            )
        }

        is MessageContent.RestrictedAsset -> MessageEntityContent.RestrictedAsset(this.mimeType, this.sizeInBytes, this.name)

        // We store the encoded data in case we decide to try to decrypt them again in the future
        is MessageContent.FailedDecryption -> MessageEntityContent.FailedDecryption(
            this.encodedData,
            this.isDecryptionResolved,
            this.senderUserId.toDao(),
            this.clientId?.value
        )

        // We store the unknown fields of the message in case we want to start handling them in the future
        is MessageContent.Unknown -> MessageEntityContent.Unknown(this.typeName, this.encodedData)

        // We don't care about the content of these messages as they are only used to perform other actions, i.e. update the content of a
        // previously stored message, delete the content of a previously stored message, etc... Therefore, we map their content to Unknown
        is MessageContent.Knock -> MessageEntityContent.Knock(hotKnock = this.hotKnock)
    }

    private fun MessageContent.System.toMessageEntityContent(): MessageEntityContent.System = when (this) {
        is MessageContent.MemberChange -> {
            val memberUserIdList = this.members.map { it.toDao() }
            when (this) {
                is MessageContent.MemberChange.Added ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.ADDED)

                is MessageContent.MemberChange.Removed ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED)
            }
        }

        is MessageContent.CryptoSessionReset -> MessageEntityContent.CryptoSessionReset
        is MessageContent.MissedCall -> MessageEntityContent.MissedCall
        is MessageContent.ConversationRenamed -> MessageEntityContent.ConversationRenamed(conversationName)
        is MessageContent.TeamMemberRemoved -> MessageEntityContent.TeamMemberRemoved(userName)
    }

    private fun MessageEntityContent.Regular.toMessageContent(hidden: Boolean): MessageContent.Regular = when (this) {
        is MessageEntityContent.Text -> {
            val quotedMessageDetails = this.quotedMessage?.let {
                MessageContent.QuotedMessageDetails(
                    senderId = it.senderId.toModel(),
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
            value = MapperProvider.assetMapper().fromAssetEntityToAssetContent(this)
        )

        is MessageEntityContent.Knock -> MessageContent.Knock(this.hotKnock)

        is MessageEntityContent.RestrictedAsset -> MessageContent.RestrictedAsset(
            this.mimeType, this.assetSizeInBytes, this.assetName
        )

        is MessageEntityContent.Unknown -> MessageContent.Unknown(this.typeName, this.encodedData, hidden)
        is MessageEntityContent.FailedDecryption -> MessageContent.FailedDecryption(
            this.encodedData,
            this.isDecryptionResolved,
            this.senderUserId.toModel(),
            ClientId(this.senderClientId.orEmpty())
        )
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
            val memberList = this.memberUserIdList.map { it.toModel() }
            when (this.memberChangeType) {
                MessageEntity.MemberChangeType.ADDED -> MessageContent.MemberChange.Added(memberList)
                MessageEntity.MemberChangeType.REMOVED -> MessageContent.MemberChange.Removed(memberList)
            }
        }

        is MessageEntityContent.MissedCall -> MessageContent.MissedCall
        is MessageEntityContent.ConversationRenamed -> MessageContent.ConversationRenamed(conversationName)
        is MessageEntityContent.TeamMemberRemoved -> MessageContent.TeamMemberRemoved(userName)
        is MessageEntityContent.CryptoSessionReset -> MessageContent.CryptoSessionReset
    }
}

fun Message.Visibility.toEntityVisibility(): MessageEntity.Visibility = when (this) {
    Message.Visibility.VISIBLE -> MessageEntity.Visibility.VISIBLE
    Message.Visibility.HIDDEN -> MessageEntity.Visibility.HIDDEN
    Message.Visibility.DELETED -> MessageEntity.Visibility.DELETED
}

fun MessageEntity.Visibility.toModel(): Message.Visibility = when (this) {
    MessageEntity.Visibility.VISIBLE -> Message.Visibility.VISIBLE
    MessageEntity.Visibility.HIDDEN -> Message.Visibility.HIDDEN
    MessageEntity.Visibility.DELETED -> Message.Visibility.DELETED
}

private fun MessagePreviewEntityContent.toMessageContent(): MessagePreviewContent = when (this) {
    is MessagePreviewEntityContent.Asset -> MessagePreviewContent.WithUser.Asset(username = senderName, type = type.toModel())
    is MessagePreviewEntityContent.ConversationNameChange -> MessagePreviewContent.WithUser.ConversationNameChange(adminName)
    is MessagePreviewEntityContent.Knock -> MessagePreviewContent.WithUser.Knock(senderName)
    is MessagePreviewEntityContent.MemberChange -> when (type) {
        MessageEntity.MemberChangeType.ADDED -> MessagePreviewContent.WithUser.MembersAdded(adminName = adminName, count = count)
        MessageEntity.MemberChangeType.REMOVED -> MessagePreviewContent.WithUser.MembersRemoved(adminName = adminName, count = count)
    }

    is MessagePreviewEntityContent.MentionedSelf -> MessagePreviewContent.WithUser.MentionedSelf(senderName)
    is MessagePreviewEntityContent.MissedCall -> MessagePreviewContent.WithUser.MissedCall(senderName)
    is MessagePreviewEntityContent.QuotedSelf -> MessagePreviewContent.WithUser.QuotedSelf(senderName)
    is MessagePreviewEntityContent.TeamMemberRemoved -> MessagePreviewContent.WithUser.TeamMemberRemoved(userName)
    is MessagePreviewEntityContent.Text -> MessagePreviewContent.WithUser.Text(username = senderName, messageBody = messageBody)
    is MessagePreviewEntityContent.CryptoSessionReset -> MessagePreviewContent.CryptoSessionReset
    MessagePreviewEntityContent.Unknown -> MessagePreviewContent.Unknown
}

fun AssetTypeEntity.toModel(): AssetType = when (this) {
    AssetTypeEntity.IMAGE -> AssetType.IMAGE
    AssetTypeEntity.VIDEO -> AssetType.VIDEO
    AssetTypeEntity.AUDIO -> AssetType.AUDIO
    AssetTypeEntity.ASSET -> AssetType.ASSET
    AssetTypeEntity.FILE -> AssetType.FILE
}
