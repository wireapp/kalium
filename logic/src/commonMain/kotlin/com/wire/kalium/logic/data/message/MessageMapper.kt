/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMessage
import com.wire.kalium.logic.data.asset.toDao
import com.wire.kalium.logic.data.asset.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.toDao
import com.wire.kalium.logic.data.conversation.toModel
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.attachment.MessageAttachmentMapper
import com.wire.kalium.logic.data.message.attachment.toModel
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.toModel
import com.wire.kalium.logic.data.notification.LocalNotificationCommentType
import com.wire.kalium.logic.data.notification.LocalNotificationMessage
import com.wire.kalium.logic.data.notification.LocalNotificationMessageAuthor
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.persistence.dao.asset.AssetMessageEntity
import com.wire.kalium.persistence.dao.message.AssetTypeEntity
import com.wire.kalium.persistence.dao.message.ButtonEntity
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import com.wire.kalium.persistence.dao.message.MessageAssetStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.dao.message.MessagePreviewEntity
import com.wire.kalium.persistence.dao.message.MessagePreviewEntityContent
import com.wire.kalium.persistence.dao.message.NotificationMessageEntity
import com.wire.kalium.persistence.dao.message.draft.MessageDraftEntity
import kotlinx.datetime.Instant
import okio.Path.Companion.toPath
import kotlin.time.DurationUnit
import kotlin.time.toDuration

interface MessageMapper {
    fun fromMessageToEntity(message: Message.Standalone): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message.Standalone
    fun fromAssetEntityToAssetMessage(message: AssetMessageEntity): AssetMessage
    fun fromEntityToMessagePreview(message: MessagePreviewEntity): MessagePreview
    fun fromDraftToMessagePreview(message: MessageDraftEntity): MessagePreview
    fun fromMessageToLocalNotificationMessage(message: NotificationMessageEntity): LocalNotificationMessage?
    fun toMessageEntityContent(regularMessage: MessageContent.Regular): MessageEntityContent.Regular
}

@Suppress("TooManyFunctions")
class MessageMapperImpl(
    private val selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = MapperProvider.linkPreviewMapper(),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
    private val userMapper: UserMapper = MapperProvider.userMapper(),
    private val attachmentsMapper: MessageAttachmentMapper = MapperProvider.attachmentsMapper(),
) : MessageMapper {

    override fun fromMessageToEntity(message: Message.Standalone): MessageEntity =
        when (message) {
            is Message.Regular -> mapFromRegularMessage(message)
            is Message.System -> mapFromSystemMessage(message)
        }

    private fun mapFromRegularMessage(
        message: Message.Regular
    ) = MessageEntity.Regular(
        id = message.id,
        content = toMessageEntityContent(message.content),
        conversationId = message.conversationId.toDao(),
        date = message.date,
        senderUserId = message.senderUserId.toDao(),
        senderClientId = message.senderClientId.value,
        status = message.status.toEntityStatus(),
        readCount = message.status.let { if (it is Message.Status.Read) it.readCount else 0 },
        editStatus = message.editStatus.let {
            when (it) {
                is Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
                is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(it.lastEditInstant)
            }
        },
        expireAfterMs = message.expirationData?.expireAfter?.inWholeMilliseconds,
        selfDeletionEndDate = message.expirationData?.selfDeletionStatus?.let {
            when (it) {
                is Message.ExpirationData.SelfDeletionStatus.Started -> it.selfDeletionEndDate
                is Message.ExpirationData.SelfDeletionStatus.NotStarted -> null
            }
        },
        visibility = message.visibility.toEntityVisibility(),
        senderName = message.senderUserName,
        isSelfMessage = message.isSelfMessage,
        expectsReadConfirmation = message.expectsReadConfirmation
    )

    private fun mapFromSystemMessage(
        message: Message.System
    ) = MessageEntity.System(
        id = message.id,
        content = message.content.toMessageEntityContent(),
        conversationId = message.conversationId.toDao(),
        date = message.date,
        senderUserId = message.senderUserId.toDao(),
        status = message.status.toEntityStatus(),
        visibility = message.visibility.toEntityVisibility(),
        senderName = message.senderUserName,
        expireAfterMs = message.expirationData?.expireAfter?.inWholeMilliseconds,
        readCount = message.status.let { if (it is Message.Status.Read) it.readCount else 0 },
        selfDeletionEndDate = message.expirationData?.selfDeletionStatus?.let {
            when (it) {
                is Message.ExpirationData.SelfDeletionStatus.Started -> it.selfDeletionEndDate
                is Message.ExpirationData.SelfDeletionStatus.NotStarted -> null
            }
        }
    )

    override fun fromEntityToMessage(message: MessageEntity): Message.Standalone {
        return when (message) {
            is MessageEntity.Regular -> mapRegularMessage(message)
            is MessageEntity.System -> mapSystemMessage(message)
        }
    }

    override fun fromAssetEntityToAssetMessage(message: AssetMessageEntity): AssetMessage {
        return AssetMessage(
            message.time,
            message.username,
            message.messageId,
            message.conversationId.toModel(),
            message.assetId,
            message.width,
            message.height,
            message.assetPath?.toPath(),
            message.isSelfAsset
        )
    }

    private fun mapRegularMessage(message: MessageEntity.Regular) = Message.Regular(
        id = message.id,
        content = message.content.toMessageContent(message.visibility.toModel() == Message.Visibility.HIDDEN, selfUserId),
        conversationId = message.conversationId.toModel(),
        date = message.date,
        senderUserId = message.senderUserId.toModel(),
        senderClientId = ClientId(message.senderClientId),
        status = message.status.toModel(message.readCount),
        editStatus = when (val editStatus = message.editStatus) {
            MessageEntity.EditStatus.NotEdited -> Message.EditStatus.NotEdited
            is MessageEntity.EditStatus.Edited -> Message.EditStatus.Edited(editStatus.lastDate)
        },
        expirationData = message.expireAfterMs?.let {
            Message.ExpirationData(
                expireAfter = it.toDuration(DurationUnit.MILLISECONDS),
                selfDeletionStatus = message.selfDeletionEndDate
                    ?.let { Message.ExpirationData.SelfDeletionStatus.Started(it) }
                    ?: Message.ExpirationData.SelfDeletionStatus.NotStarted
            )
        },
        visibility = message.visibility.toModel(),
        reactions = Message.Reactions(message.reactions.totalReactions, message.reactions.selfUserReactions),
        senderUserName = message.senderName,
        isSelfMessage = message.isSelfMessage,
        expectsReadConfirmation = message.expectsReadConfirmation,
        deliveryStatus = when (val recipientsFailure = message.deliveryStatus) {
            is DeliveryStatusEntity.CompleteDelivery -> DeliveryStatus.CompleteDelivery
            is DeliveryStatusEntity.PartialDelivery -> DeliveryStatus.PartialDelivery(
                recipientsFailedWithNoClients = recipientsFailure.recipientsFailedWithNoClients.map { it.toModel() },
                recipientsFailedDelivery = recipientsFailure.recipientsFailedDelivery.map { it.toModel() }
            )
        },
        sender = message.sender?.let {
            if (message.isSelfMessage) {
                userMapper.fromUserDetailsEntityToSelfUser(it)
            } else {
                userMapper.fromUserDetailsEntityToOtherUser(it)
            }
        }
    )

    private fun mapSystemMessage(message: MessageEntity.System) = Message.System(
        id = message.id,
        content = message.content.toMessageContent(),
        conversationId = message.conversationId.toModel(),
        date = message.date,
        senderUserId = message.senderUserId.toModel(),
        status = message.status.toModel(message.readCount),
        visibility = message.visibility.toModel(),
        senderUserName = message.senderName,
        expirationData = message.expireAfterMs?.let {
            Message.ExpirationData(
                expireAfter = it.toDuration(DurationUnit.MILLISECONDS),
                selfDeletionStatus = message.selfDeletionEndDate
                    ?.let { Message.ExpirationData.SelfDeletionStatus.Started(it) }
                    ?: Message.ExpirationData.SelfDeletionStatus.NotStarted
            )
        },
        sender = message.sender?.let {
            if (message.isSelfMessage) {
                userMapper.fromUserDetailsEntityToSelfUser(it)
            } else {
                userMapper.fromUserDetailsEntityToOtherUser(it)
            }
        }
    )

    override fun fromEntityToMessagePreview(message: MessagePreviewEntity): MessagePreview {
        return MessagePreview(
            id = message.id,
            conversationId = message.conversationId.toModel(),
            content = message.content.toMessageContent(),
            visibility = message.visibility.toModel(),
            isSelfMessage = message.isSelfMessage,
            senderUserId = message.senderUserId.toModel()
        )
    }

    override fun fromDraftToMessagePreview(message: MessageDraftEntity): MessagePreview {
        return MessagePreview(
            id = message.conversationId.toString(),
            conversationId = message.conversationId.toModel(),
            content = MessagePreviewContent.Draft(message.text),
            visibility = Message.Visibility.VISIBLE,
            isSelfMessage = true,
            senderUserId = selfUserId
        )
    }

    @Suppress("ComplexMethod", "LongMethod")
    override fun fromMessageToLocalNotificationMessage(
        message: NotificationMessageEntity
    ): LocalNotificationMessage? {
        val sender = LocalNotificationMessageAuthor(
            message.senderName.orEmpty(),
            message.senderImage?.toModel()
        )
        if (message.isSelfDelete) {
            return when (message.contentType) {
                MessageEntity.ContentType.KNOCK -> LocalNotificationMessage.SelfDeleteKnock(
                    message.id,
                    message.date
                )

                else -> LocalNotificationMessage.SelfDeleteMessage(
                    message.id,
                    message.date
                )
            }
        }
        return when (message.contentType) {
            MessageEntity.ContentType.TEXT -> LocalNotificationMessage.Text(
                messageId = message.id,
                author = sender,
                text = message.text.orEmpty(),
                time = message.date,
                isQuotingSelfUser = message.isQuotingSelf
            )

            MessageEntity.ContentType.ASSET -> {
                val type = message.assetMimeType?.contains("image/")?.let {
                    if (it) LocalNotificationCommentType.PICTURE else LocalNotificationCommentType.FILE
                } ?: LocalNotificationCommentType.FILE

                LocalNotificationMessage.Comment(message.id, sender, message.date, type)
            }

            MessageEntity.ContentType.KNOCK -> {
                LocalNotificationMessage.Knock(
                    message.id,
                    sender,
                    message.date
                )
            }

            MessageEntity.ContentType.MISSED_CALL -> {
                LocalNotificationMessage.Comment(
                    message.id,
                    sender,
                    message.date,
                    LocalNotificationCommentType.MISSED_CALL
                )
            }

            MessageEntity.ContentType.LOCATION -> LocalNotificationMessage.Comment(
                message.id,
                sender,
                message.date,
                LocalNotificationCommentType.LOCATION
            )

            MessageEntity.ContentType.MEMBER_CHANGE -> null
            MessageEntity.ContentType.RESTRICTED_ASSET -> null
            MessageEntity.ContentType.CONVERSATION_RENAMED -> null
            MessageEntity.ContentType.UNKNOWN -> null
            MessageEntity.ContentType.FAILED_DECRYPTION -> null
            MessageEntity.ContentType.REMOVED_FROM_TEAM -> null
            MessageEntity.ContentType.CRYPTO_SESSION_RESET -> null
            MessageEntity.ContentType.NEW_CONVERSATION_RECEIPT_MODE -> null
            MessageEntity.ContentType.CONVERSATION_RECEIPT_MODE_CHANGED -> null
            MessageEntity.ContentType.HISTORY_LOST -> null
            MessageEntity.ContentType.HISTORY_LOST_PROTOCOL_CHANGED -> null
            MessageEntity.ContentType.CONVERSATION_MESSAGE_TIMER_CHANGED -> null
            MessageEntity.ContentType.CONVERSATION_CREATED -> null
            MessageEntity.ContentType.MLS_WRONG_EPOCH_WARNING -> null
            MessageEntity.ContentType.CONVERSATION_DEGRADED_MLS -> null
            MessageEntity.ContentType.CONVERSATION_DEGRADED_PROTEUS -> null
            MessageEntity.ContentType.COMPOSITE -> null
            MessageEntity.ContentType.FEDERATION -> null
            MessageEntity.ContentType.CONVERSATION_VERIFIED_MLS -> null
            MessageEntity.ContentType.CONVERSATION_VERIFIED_PROTEUS -> null
            MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED -> null
            MessageEntity.ContentType.CONVERSATION_PROTOCOL_CHANGED_DURING_CALL -> null
            MessageEntity.ContentType.CONVERSATION_STARTED_UNVERIFIED_WARNING -> null
            MessageEntity.ContentType.LEGAL_HOLD -> null

            MessageEntity.ContentType.MULTIPART -> LocalNotificationMessage.Text(
                messageId = message.id,
                author = sender,
                text = message.text.orEmpty(),
                time = message.date,
                isQuotingSelfUser = message.isQuotingSelf
            )
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    override fun toMessageEntityContent(regularMessage: MessageContent.Regular): MessageEntityContent.Regular = when (regularMessage) {
        is MessageContent.Text -> toTextEntity(regularMessage)

        is MessageContent.Asset -> with(regularMessage.value) {
            val metadata = metadata
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

        is MessageContent.RestrictedAsset -> MessageEntityContent.RestrictedAsset(
            regularMessage.mimeType,
            regularMessage.sizeInBytes,
            regularMessage.name
        )

        // We store the encoded data in case we decide to try to decrypt them again in the future
        is MessageContent.FailedDecryption -> MessageEntityContent.FailedDecryption(
            regularMessage.encodedData,
            regularMessage.errorCode,
            regularMessage.isDecryptionResolved,
            regularMessage.senderUserId.toDao(),
            regularMessage.clientId?.value
        )

        // We store the unknown fields of the message in case we want to start handling them in the future
        is MessageContent.Unknown -> MessageEntityContent.Unknown(regularMessage.typeName, regularMessage.encodedData)

        // We don't care about the content of these messages as they are only used to perform other actions, i.e. update the content of a
        // previously stored message, delete the content of a previously stored message, etc... Therefore, we map their content to Unknown
        is MessageContent.Knock -> MessageEntityContent.Knock(hotKnock = regularMessage.hotKnock)
        is MessageContent.Composite -> MessageEntityContent.Composite(
            text = regularMessage.textContent?.let(this::toTextEntity),
            buttonList = regularMessage.buttonList.map {
                ButtonEntity(
                    id = it.id,
                    text = it.text,
                    isSelected = it.isSelected
                )
            },
        )

        is MessageContent.Location -> MessageEntityContent.Location(
            latitude = regularMessage.latitude,
            longitude = regularMessage.longitude,
            name = regularMessage.name,
            zoom = regularMessage.zoom
        )

        is MessageContent.Multipart -> MessageEntityContent.Multipart(
                messageBody = regularMessage.value,
                linkPreview = regularMessage.linkPreviews.map(linkPreviewMapper::fromModelToDao),
                mentions = regularMessage.mentions.map(messageMentionMapper::fromModelToDao),
                attachments = regularMessage.attachments.map {
                    attachmentsMapper.fromModelToDao(it)
                                                             },
                quotedMessageId = regularMessage.quotedMessageReference?.quotedMessageId,
                isQuoteVerified = regularMessage.quotedMessageReference?.isVerified,
            )
    }

    private fun toTextEntity(textContent: MessageContent.Text): MessageEntityContent.Text = MessageEntityContent.Text(
        messageBody = textContent.value,
        linkPreview = textContent.linkPreviews.map(linkPreviewMapper::fromModelToDao),
        mentions = textContent.mentions.map(messageMentionMapper::fromModelToDao),
        quotedMessageId = textContent.quotedMessageReference?.quotedMessageId,
        isQuoteVerified = textContent.quotedMessageReference?.isVerified,
    )
}

@Suppress("ComplexMethod")
fun MessageEntityContent.System.toMessageContent(): MessageContent.System = when (this) {
    is MessageEntityContent.MemberChange -> {
        val memberList = this.memberUserIdList.map { it.toModel() }
        when (this.memberChangeType) {
            MessageEntity.MemberChangeType.ADDED -> MessageContent.MemberChange.Added(memberList)
            MessageEntity.MemberChangeType.REMOVED -> MessageContent.MemberChange.Removed(memberList)
            MessageEntity.MemberChangeType.CREATION_ADDED -> MessageContent.MemberChange.CreationAdded(memberList)
            MessageEntity.MemberChangeType.FAILED_TO_ADD_FEDERATION ->
                MessageContent.MemberChange.FailedToAdd(memberList, MessageContent.MemberChange.FailedToAdd.Type.Federation)

            MessageEntity.MemberChangeType.FAILED_TO_ADD_LEGAL_HOLD ->
                MessageContent.MemberChange.FailedToAdd(memberList, MessageContent.MemberChange.FailedToAdd.Type.LegalHold)

            MessageEntity.MemberChangeType.FAILED_TO_ADD_UNKNOWN ->
                MessageContent.MemberChange.FailedToAdd(memberList, MessageContent.MemberChange.FailedToAdd.Type.Unknown)

            MessageEntity.MemberChangeType.FEDERATION_REMOVED -> MessageContent.MemberChange.FederationRemoved(memberList)
            MessageEntity.MemberChangeType.REMOVED_FROM_TEAM -> MessageContent.MemberChange.RemovedFromTeam(memberList)
        }
    }

    is MessageEntityContent.MissedCall -> MessageContent.MissedCall
    is MessageEntityContent.ConversationRenamed -> MessageContent.ConversationRenamed(conversationName)
    is MessageEntityContent.TeamMemberRemoved -> MessageContent.TeamMemberRemoved(userName)
    is MessageEntityContent.CryptoSessionReset -> MessageContent.CryptoSessionReset
    is MessageEntityContent.NewConversationReceiptMode -> MessageContent.NewConversationReceiptMode(receiptMode)
    is MessageEntityContent.ConversationReceiptModeChanged -> MessageContent.ConversationReceiptModeChanged(receiptMode)
    is MessageEntityContent.HistoryLost -> MessageContent.HistoryLost
    is MessageEntityContent.HistoryLostProtocolChanged -> MessageContent.HistoryLostProtocolChanged
    is MessageEntityContent.ConversationMessageTimerChanged -> MessageContent.ConversationMessageTimerChanged(messageTimer)
    is MessageEntityContent.ConversationCreated -> MessageContent.ConversationCreated
    is MessageEntityContent.MLSWrongEpochWarning -> MessageContent.MLSWrongEpochWarning
    is MessageEntityContent.ConversationDegradedMLS -> MessageContent.ConversationDegradedMLS
    is MessageEntityContent.ConversationVerifiedMLS -> MessageContent.ConversationVerifiedMLS
    is MessageEntityContent.ConversationDegradedProteus -> MessageContent.ConversationDegradedProteus
    is MessageEntityContent.ConversationVerifiedProteus -> MessageContent.ConversationVerifiedProteus
    is MessageEntityContent.Federation -> when (type) {
        MessageEntity.FederationType.DELETE -> MessageContent.FederationStopped.Removed(domainList.first())
        MessageEntity.FederationType.CONNECTION_REMOVED -> MessageContent.FederationStopped.ConnectionRemoved(domainList)
    }

    is MessageEntityContent.ConversationProtocolChanged -> MessageContent.ConversationProtocolChanged(protocol.toModel())
    is MessageEntityContent.ConversationProtocolChangedDuringACall -> MessageContent.ConversationProtocolChangedDuringACall
    is MessageEntityContent.ConversationStartedUnverifiedWarning -> MessageContent.ConversationStartedUnverifiedWarning
    is MessageEntityContent.LegalHold -> {
        when (this.type) {
            MessageEntity.LegalHoldType.DISABLED_FOR_CONVERSATION -> MessageContent.LegalHold.ForConversation.Disabled
            MessageEntity.LegalHoldType.ENABLED_FOR_CONVERSATION -> MessageContent.LegalHold.ForConversation.Enabled
            MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS ->
                MessageContent.LegalHold.ForMembers.Disabled(this.memberUserIdList.map { it.toModel() })

            MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS ->
                MessageContent.LegalHold.ForMembers.Enabled(this.memberUserIdList.map { it.toModel() })
        }
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

@Suppress("ComplexMethod")
private fun MessagePreviewEntityContent.toMessageContent(): MessagePreviewContent = when (this) {
    is MessagePreviewEntityContent.Asset -> MessagePreviewContent.WithUser.Asset(username = senderName, type = type.toModel())
    is MessagePreviewEntityContent.ConversationNameChange -> MessagePreviewContent.WithUser.ConversationNameChange(adminName)
    is MessagePreviewEntityContent.Knock -> MessagePreviewContent.WithUser.Knock(senderName)
    is MessagePreviewEntityContent.MemberJoined -> MessagePreviewContent.WithUser.MemberJoined(senderName)
    is MessagePreviewEntityContent.MemberLeft -> MessagePreviewContent.WithUser.MemberLeft(senderName)
    is MessagePreviewEntityContent.MembersAdded -> MessagePreviewContent.WithUser.MembersAdded(
        username = senderName,
        isSelfUserAdded = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.ConversationMembersRemoved -> MessagePreviewContent.WithUser.ConversationMembersRemoved(
        username = senderName,
        isSelfUserRemoved = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.FederatedMembersRemoved -> MessagePreviewContent.FederatedMembersRemoved(
        isSelfUserRemoved = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.MembersCreationAdded -> MessagePreviewContent.WithUser.MembersCreationAdded(
        username = senderName,
        isSelfUserRemoved = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.MembersFailedToAdded -> MessagePreviewContent.WithUser.MembersFailedToAdd(
        username = senderName,
        isSelfUserRemoved = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.TeamMembersRemoved -> MessagePreviewContent.WithUser.TeamMembersRemoved(
        username = senderName,
        isSelfUserRemoved = isContainSelfUserId,
        otherUserIdList = otherUserIdList.map { it.toModel() }
    )

    is MessagePreviewEntityContent.Ephemeral -> MessagePreviewContent.Ephemeral(isGroupConversation)
    is MessagePreviewEntityContent.MentionedSelf -> MessagePreviewContent.WithUser.MentionedSelf(senderName)
    is MessagePreviewEntityContent.MissedCall -> MessagePreviewContent.WithUser.MissedCall(senderName)
    is MessagePreviewEntityContent.QuotedSelf -> MessagePreviewContent.WithUser.QuotedSelf(senderName)
    is MessagePreviewEntityContent.TeamMemberRemoved_Legacy -> MessagePreviewContent.WithUser.TeamMemberRemoved(userName)
    is MessagePreviewEntityContent.Text -> MessagePreviewContent.WithUser.Text(username = senderName, messageBody = messageBody)
    is MessagePreviewEntityContent.CryptoSessionReset -> MessagePreviewContent.CryptoSessionReset
    MessagePreviewEntityContent.Unknown -> MessagePreviewContent.Unknown
    is MessagePreviewEntityContent.Composite -> MessagePreviewContent.WithUser.Composite(username = senderName, messageBody = messageBody)
    is MessagePreviewEntityContent.ConversationVerifiedMls -> MessagePreviewContent.VerificationChanged.VerifiedMls
    is MessagePreviewEntityContent.ConversationVerifiedProteus -> MessagePreviewContent.VerificationChanged.VerifiedProteus
    is MessagePreviewEntityContent.ConversationVerificationDegradedMls -> MessagePreviewContent.VerificationChanged.DegradedMls
    is MessagePreviewEntityContent.ConversationVerificationDegradedProteus -> MessagePreviewContent.VerificationChanged.DegradedProteus
    is MessagePreviewEntityContent.Location -> MessagePreviewContent.WithUser.Location(username = senderName)
    is MessagePreviewEntityContent.Deleted -> MessagePreviewContent.WithUser.Deleted(username = senderName)
}

fun AssetTypeEntity.toModel(): AssetType = when (this) {
    AssetTypeEntity.IMAGE -> AssetType.IMAGE
    AssetTypeEntity.VIDEO -> AssetType.VIDEO
    AssetTypeEntity.AUDIO -> AssetType.AUDIO
    AssetTypeEntity.GENERIC_ASSET -> AssetType.GENERIC_ASSET
}

fun Message.Status.toEntityStatus() =
    when (this) {
        Message.Status.Delivered -> MessageEntity.Status.DELIVERED
        Message.Status.Pending -> MessageEntity.Status.PENDING
        is Message.Status.Read -> MessageEntity.Status.READ
        Message.Status.Sent -> MessageEntity.Status.SENT
        Message.Status.Failed -> MessageEntity.Status.FAILED
        Message.Status.FailedRemotely -> MessageEntity.Status.FAILED_REMOTELY
    }

fun MessageEntity.Status.toModel(readCount: Long) =
    when (this) {
        MessageEntity.Status.PENDING -> Message.Status.Pending
        MessageEntity.Status.SENT -> Message.Status.Sent
        MessageEntity.Status.DELIVERED -> Message.Status.Delivered
        MessageEntity.Status.READ -> Message.Status.Read(readCount)
        MessageEntity.Status.FAILED -> Message.Status.Failed
        MessageEntity.Status.FAILED_REMOTELY -> Message.Status.FailedRemotely
    }

@Suppress("LongMethod", "CyclomaticComplexMethod")
fun MessageEntityContent.Regular.toMessageContent(hidden: Boolean, selfUserId: UserId): MessageContent.Regular = when (this) {
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
            mentions = this.mentions.map { it.toModel(selfUserId = selfUserId) },
            quotedMessageReference = quotedMessageId?.let {
                MessageContent.QuoteReference(
                    quotedMessageId = it,
                    quotedMessageSha256 = null,
                    isVerified = quotedMessageDetails?.isVerified ?: false
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
        this.code,
        this.isDecryptionResolved,
        this.senderUserId.toModel(),
        ClientId(this.senderClientId.orEmpty())
    )

    is MessageEntityContent.Composite -> MessageContent.Composite(
        this.text?.toMessageContent(hidden, selfUserId) as? MessageContent.Text,
        this.buttonList.map {
            MessageContent.Composite.Button(
                text = it.text,
                id = it.id,
                isSelected = it.isSelected
            )
        }
    )

    is MessageEntityContent.Location -> MessageContent.Location(
        latitude = this.latitude,
        longitude = this.longitude,
        name = this.name,
        zoom = this.zoom
    )

    is MessageEntityContent.Multipart -> {
        val quotedMessageDetails = quotedMessage?.let {
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
        MessageContent.Multipart(
            value = messageBody,
            mentions = mentions.map { it.toModel(selfUserId = selfUserId) },
            quotedMessageReference = quotedMessageId?.let {
                MessageContent.QuoteReference(
                    quotedMessageId = it,
                    quotedMessageSha256 = null,
                    isVerified = quotedMessageDetails?.isVerified ?: false
                )
            },
            quotedMessageDetails = quotedMessageDetails,
            attachments = attachments.map { it.toModel() }
        )
    }
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

    it.contentType == MessageEntity.ContentType.LOCATION -> {
        MessageContent.QuotedMessageDetails.Location(
            locationName = it.locationName,
        )
    }

    // If a new content type can be replied to (Pings, for example), fallback to Invalid
    else -> MessageContent.QuotedMessageDetails.Invalid
}

@Suppress("ComplexMethod", "LongMethod")
fun MessageContent.System.toMessageEntityContent(): MessageEntityContent.System = when (this) {
    is MessageContent.MemberChange -> {
        val memberUserIdList = this.members.map { it.toDao() }
        when (this) {
            is MessageContent.MemberChange.Added ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.ADDED)

            is MessageContent.MemberChange.Removed ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED)

            is MessageContent.MemberChange.CreationAdded ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.CREATION_ADDED)

            is MessageContent.MemberChange.FailedToAdd ->
                when (this.type) {
                    MessageContent.MemberChange.FailedToAdd.Type.Federation ->
                        MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_FEDERATION)

                    MessageContent.MemberChange.FailedToAdd.Type.LegalHold ->
                        MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_LEGAL_HOLD)

                    MessageContent.MemberChange.FailedToAdd.Type.Unknown ->
                        MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_UNKNOWN)
                }

            is MessageContent.MemberChange.FederationRemoved -> MessageEntityContent.MemberChange(
                memberUserIdList,
                MessageEntity.MemberChangeType.FEDERATION_REMOVED
            )

            is MessageContent.MemberChange.RemovedFromTeam -> MessageEntityContent.MemberChange(
                memberUserIdList,
                MessageEntity.MemberChangeType.REMOVED_FROM_TEAM
            )
        }
    }

    is MessageContent.CryptoSessionReset -> MessageEntityContent.CryptoSessionReset
    is MessageContent.MissedCall -> MessageEntityContent.MissedCall
    is MessageContent.ConversationRenamed -> MessageEntityContent.ConversationRenamed(conversationName)
    is MessageContent.TeamMemberRemoved -> MessageEntityContent.TeamMemberRemoved(userName)
    is MessageContent.NewConversationReceiptMode -> MessageEntityContent.NewConversationReceiptMode(receiptMode)
    is MessageContent.ConversationReceiptModeChanged -> MessageEntityContent.ConversationReceiptModeChanged(receiptMode)
    is MessageContent.HistoryLost -> MessageEntityContent.HistoryLost
    is MessageContent.ConversationMessageTimerChanged -> MessageEntityContent.ConversationMessageTimerChanged(messageTimer)
    is MessageContent.ConversationCreated -> MessageEntityContent.ConversationCreated
    is MessageContent.MLSWrongEpochWarning -> MessageEntityContent.MLSWrongEpochWarning
    is MessageContent.ConversationDegradedMLS -> MessageEntityContent.ConversationDegradedMLS
    is MessageContent.ConversationDegradedProteus -> MessageEntityContent.ConversationDegradedProteus
    is MessageContent.FederationStopped.ConnectionRemoved -> MessageEntityContent.Federation(
        domainList,
        MessageEntity.FederationType.CONNECTION_REMOVED
    )

    is MessageContent.FederationStopped.Removed -> MessageEntityContent.Federation(
        listOf(domain),
        MessageEntity.FederationType.DELETE
    )

    MessageContent.ConversationVerifiedMLS -> MessageEntityContent.ConversationVerifiedMLS
    MessageContent.ConversationVerifiedProteus -> MessageEntityContent.ConversationVerifiedProteus
    is MessageContent.ConversationProtocolChanged -> MessageEntityContent.ConversationProtocolChanged(protocol.toDao())
    is MessageContent.ConversationProtocolChangedDuringACall -> MessageEntityContent.ConversationProtocolChangedDuringACall
    MessageContent.HistoryLostProtocolChanged -> MessageEntityContent.HistoryLostProtocolChanged
    is MessageContent.ConversationStartedUnverifiedWarning -> MessageEntityContent.ConversationStartedUnverifiedWarning
    is MessageContent.LegalHold -> when (this) {
        MessageContent.LegalHold.ForConversation.Disabled ->
            MessageEntityContent.LegalHold(emptyList(), MessageEntity.LegalHoldType.DISABLED_FOR_CONVERSATION)

        MessageContent.LegalHold.ForConversation.Enabled ->
            MessageEntityContent.LegalHold(emptyList(), MessageEntity.LegalHoldType.ENABLED_FOR_CONVERSATION)

        is MessageContent.LegalHold.ForMembers.Disabled ->
            MessageEntityContent.LegalHold(this.members.map { it.toDao() }, MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS)

        is MessageContent.LegalHold.ForMembers.Enabled ->
            MessageEntityContent.LegalHold(this.members.map { it.toDao() }, MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS)
    }
}

fun MessageAssetStatus.toDao(): MessageAssetStatusEntity {
    return MessageAssetStatusEntity(
        id = id,
        conversationId = conversationId.toDao(),
        transferStatus = transferStatus.toDao()
    )
}

fun MessageAssetStatusEntity.toModel(): MessageAssetStatus {
    return MessageAssetStatus(
        id = id,
        conversationId = conversationId.toModel(),
        transferStatus = transferStatus.toModel()
    )
}
