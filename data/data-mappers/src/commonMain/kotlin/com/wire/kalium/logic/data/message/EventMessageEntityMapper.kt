/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.conversation.toDao
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.attachment.MessageAttachmentMapper
import com.wire.kalium.logic.data.message.attachment.MessageAttachmentMapperImpl
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapperImpl
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapperImpl
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.ButtonEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.util.InternalKaliumApi

/** Maps incoming domain messages to the entities required by receiver-side persistence. */
@InternalKaliumApi
public interface EventMessageEntityMapper {
    public fun fromMessageToEntity(message: Message.Standalone): MessageEntity
    public fun toMessageEntityContent(content: MessageContent.Regular): MessageEntityContent.Regular
}

@InternalKaliumApi
public class EventMessageEntityMapperImpl public constructor(
    selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = LinkPreviewMapperImpl(),
    private val messageMentionMapper: MessageMentionMapper = MessageMentionMapperImpl(IdMapper(), selfUserId),
    private val attachmentsMapper: MessageAttachmentMapper = MessageAttachmentMapperImpl(),
) : EventMessageEntityMapper {

    override fun fromMessageToEntity(message: Message.Standalone): MessageEntity = when (message) {
        is Message.Regular -> mapRegularMessage(message)
        is Message.System -> mapSystemMessage(message)
    }

    private fun mapRegularMessage(message: Message.Regular): MessageEntity.Regular = MessageEntity.Regular(
        id = message.id,
        content = toMessageEntityContent(message.content),
        conversationId = message.conversationId.toDao(),
        date = message.date,
        senderUserId = message.senderUserId.toDao(),
        senderClientId = message.senderClientId.value,
        status = message.status.toEntityStatus(),
        readCount = message.status.let { if (it is Message.Status.Read) it.readCount else 0 },
        editStatus = when (val editStatus = message.editStatus) {
            is Message.EditStatus.NotEdited -> MessageEntity.EditStatus.NotEdited
            is Message.EditStatus.Edited -> MessageEntity.EditStatus.Edited(editStatus.lastEditInstant)
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
        expectsReadConfirmation = message.expectsReadConfirmation,
    )

    private fun mapSystemMessage(message: Message.System): MessageEntity.System = MessageEntity.System(
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
        },
    )

    @Suppress("ComplexMethod", "LongMethod")
    override fun toMessageEntityContent(content: MessageContent.Regular): MessageEntityContent.Regular = when (content) {
        is MessageContent.Text -> toTextEntity(content)
        is MessageContent.Asset -> with(content.value) {
            val assetMetadata = metadata
            val assetWidth = when (assetMetadata) {
                is Image -> assetMetadata.width
                is Video -> assetMetadata.width
                else -> null
            }
            val assetHeight = when (assetMetadata) {
                is Image -> assetMetadata.height
                is Video -> assetMetadata.height
                else -> null
            }
            val assetDurationMs = when (assetMetadata) {
                is Video -> assetMetadata.durationMs
                is Audio -> assetMetadata.durationMs
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
                assetNormalizedLoudness = (assetMetadata as? Audio)?.normalizedLoudness,
            )
        }
        is MessageContent.RestrictedAsset -> MessageEntityContent.RestrictedAsset(
            content.mimeType,
            content.sizeInBytes,
            content.name,
        )
        is MessageContent.FailedDecryption -> MessageEntityContent.FailedDecryption(
            content.encodedData,
            content.errorCode,
            content.isDecryptionResolved,
            content.senderUserId.toDao(),
            content.clientId?.value,
        )
        is MessageContent.Unknown -> MessageEntityContent.Unknown(content.typeName, content.encodedData)
        is MessageContent.Knock -> MessageEntityContent.Knock(hotKnock = content.hotKnock)
        is MessageContent.Composite -> MessageEntityContent.Composite(
            text = content.textContent?.let(::toTextEntity),
            buttonList = content.buttonList.map {
                ButtonEntity(id = it.id, text = it.text, isSelected = it.isSelected)
            },
        )
        is MessageContent.Location -> MessageEntityContent.Location(
            latitude = content.latitude,
            longitude = content.longitude,
            name = content.name,
            zoom = content.zoom,
        )
        is MessageContent.Multipart -> MessageEntityContent.Multipart(
            messageBody = content.value,
            linkPreview = content.linkPreviews.map(linkPreviewMapper::fromModelToDao),
            mentions = content.mentions.map(messageMentionMapper::fromModelToDao),
            attachments = content.attachments.mapIndexedNotNull { index, attachment ->
                attachmentsMapper.fromModelToDao(attachment)?.copy(assetIndex = index)
            },
            quotedMessageId = content.quotedMessageReference?.quotedMessageId,
            isQuoteVerified = content.quotedMessageReference?.isVerified,
        )
    }

    private fun toTextEntity(content: MessageContent.Text): MessageEntityContent.Text = MessageEntityContent.Text(
        messageBody = content.value,
        linkPreview = content.linkPreviews.map(linkPreviewMapper::fromModelToDao),
        mentions = content.mentions.map(messageMentionMapper::fromModelToDao),
        quotedMessageId = content.quotedMessageReference?.quotedMessageId,
        isQuoteVerified = content.quotedMessageReference?.isVerified,
    )
}

@InternalKaliumApi
public fun Message.Visibility.toEntityVisibility(): MessageEntity.Visibility = when (this) {
    Message.Visibility.VISIBLE -> MessageEntity.Visibility.VISIBLE
    Message.Visibility.HIDDEN -> MessageEntity.Visibility.HIDDEN
    Message.Visibility.DELETED -> MessageEntity.Visibility.DELETED
}

@InternalKaliumApi
public fun Message.Status.toEntityStatus(): MessageEntity.Status = when (this) {
    Message.Status.Delivered -> MessageEntity.Status.DELIVERED
    Message.Status.Pending -> MessageEntity.Status.PENDING
    is Message.Status.Read -> MessageEntity.Status.READ
    Message.Status.Sent -> MessageEntity.Status.SENT
    Message.Status.Failed -> MessageEntity.Status.FAILED
    Message.Status.FailedRemotely -> MessageEntity.Status.FAILED_REMOTELY
}

@InternalKaliumApi
@Suppress("ComplexMethod", "LongMethod")
public fun MessageContent.System.toMessageEntityContent(): MessageEntityContent.System = when (this) {
    is MessageContent.MemberChange -> {
        val memberUserIdList = members.map { it.toDao() }
        when (this) {
            is MessageContent.MemberChange.Added ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.ADDED)
            is MessageContent.MemberChange.Removed ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED)
            is MessageContent.MemberChange.CreationAdded ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.CREATION_ADDED)
            is MessageContent.MemberChange.FailedToAdd -> when (type) {
                MessageContent.MemberChange.FailedToAdd.Type.Federation ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_FEDERATION)
                MessageContent.MemberChange.FailedToAdd.Type.LegalHold ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_LEGAL_HOLD)
                MessageContent.MemberChange.FailedToAdd.Type.Unknown ->
                    MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FAILED_TO_ADD_UNKNOWN)
                MessageContent.MemberChange.FailedToAdd.Type.MissingKeyPackages -> MessageEntityContent.MemberChange(
                    memberUserIdList,
                    MessageEntity.MemberChangeType.FAILED_TO_ADD_MISSING_KEY_PACKAGES,
                )
            }
            is MessageContent.MemberChange.FederationRemoved ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.FEDERATION_REMOVED)
            is MessageContent.MemberChange.RemovedFromTeam ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.REMOVED_FROM_TEAM)
            is MessageContent.MemberChange.UserPromotedToAdmin ->
                MessageEntityContent.MemberChange(memberUserIdList, MessageEntity.MemberChangeType.USER_PROMOTED_TO_ADMIN)
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
    is MessageContent.FederationStopped.ConnectionRemoved ->
        MessageEntityContent.Federation(domainList, MessageEntity.FederationType.CONNECTION_REMOVED)
    is MessageContent.FederationStopped.Removed ->
        MessageEntityContent.Federation(listOf(domain), MessageEntity.FederationType.DELETE)
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
            MessageEntityContent.LegalHold(members.map { it.toDao() }, MessageEntity.LegalHoldType.DISABLED_FOR_MEMBERS)
        is MessageContent.LegalHold.ForMembers.Enabled ->
            MessageEntityContent.LegalHold(members.map { it.toDao() }, MessageEntity.LegalHoldType.ENABLED_FOR_MEMBERS)
    }
    MessageContent.NewConversationWithCellMessage -> MessageEntityContent.NewConversationWithCellMessage
    MessageContent.NewConversationWithCellSelfDeleteDisabledMessage ->
        MessageEntityContent.NewConversationWithCellSelfDeleteDisabledMessage
    MessageContent.CellEditorAccessMessage -> MessageEntityContent.CellEditorAccessMessage
    MessageContent.CellViewerAccessMessage -> MessageEntityContent.CellViewerAccessMessage
    is MessageContent.ConversationAppsEnabledChanged -> MessageEntityContent.ConversationAppsAccessChanged(isEnabled)
}
