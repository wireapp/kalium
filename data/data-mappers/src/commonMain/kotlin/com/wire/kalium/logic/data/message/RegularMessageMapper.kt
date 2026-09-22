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

import com.wire.kalium.logic.data.asset.toDao
import com.wire.kalium.logic.data.asset.toModel
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Audio
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Image
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata.Video
import com.wire.kalium.logic.data.message.attachment.MessageAttachmentMapper
import com.wire.kalium.logic.data.message.attachment.toModel
import com.wire.kalium.logic.data.message.composite.Button
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.mention.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.message.DeliveryStatusEntity
import kotlin.time.DurationUnit
import kotlin.time.toDuration
import com.wire.kalium.persistence.dao.message.ButtonEntity
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent

import com.wire.kalium.logic.data.asset.AssetMapperImpl
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapperImpl
import com.wire.kalium.logic.data.message.mention.MessageMentionMapperImpl
import com.wire.kalium.logic.data.message.attachment.MessageAttachmentMapperImpl

/** Shared stored regular-message and content mapping used by app and receiving. */
class RegularMessageMapper(
    private val selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = LinkPreviewMapperImpl(),
    private val messageMentionMapper: MessageMentionMapper = MessageMentionMapperImpl(IdMapper(), selfUserId),
    private val attachmentsMapper: MessageAttachmentMapper = MessageAttachmentMapperImpl(),
) {
    fun fromEntity(message: MessageEntity.Regular): Message.Regular {
        val content = message.content.toMessageContent(message.visibility.toModel() == Message.Visibility.HIDDEN, selfUserId)
        return Message.Regular(
            id = message.id,
            content = content,
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
            reactions = Message.Reactions(
                reactions = message.reactions.reactions.mapValues { (_, reaction) ->
                    Message.ReactionData(
                        count = reaction.count,
                        isSelf = reaction.isSelf
                    )
                }
            ),
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
            linkPreviews = (content as? MessageContent.Text)?.linkPreviews.orEmpty()
        )
    }

    fun toEntity(
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

    @Suppress("ComplexMethod", "LongMethod")
    fun toMessageEntityContent(regularMessage: MessageContent.Regular): MessageEntityContent.Regular = when (regularMessage) {
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
            attachments = regularMessage.attachments.mapIndexedNotNull { index, attachment ->
                attachmentsMapper.fromModelToDao(attachment)?.copy(assetIndex = index)
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
                timeInstant = it.dateTime,
                editInstant = it.editTimestamp,
                quotedContent = quotedContentFromEntity(it),
                accentId = it.accentId
            )
        }
        MessageContent.Text(
            value = this.messageBody,
            linkPreviews = this.linkPreview.map { LinkPreviewMapperImpl().fromDaoToModel(it) },
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
        value = AssetMapperImpl().fromAssetEntityToAssetContent(this)
    )

    is MessageEntityContent.Knock -> MessageContent.Knock(this.hotKnock)

    is MessageEntityContent.RestrictedAsset -> MessageContent.RestrictedAsset(
        this.mimeType,
        this.assetSizeInBytes,
        this.assetName
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
            Button(
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
                timeInstant = it.dateTime,
                editInstant = it.editTimestamp,
                quotedContent = quotedContentFromEntity(it),
                accentId = it.accentId
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
            attachments = attachments
                .sortedBy { it.assetIndex }
                .mapNotNull { it.toModel() }
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

    it.contentType == MessageEntity.ContentType.MULTIPART -> {
        MessageContent.QuotedMessageDetails.Multipart(
            text = it.textBody,
        )
    }

    // If a new content type can be replied to (Pings, for example), fallback to Invalid
    else -> MessageContent.QuotedMessageDetails.Invalid
}
