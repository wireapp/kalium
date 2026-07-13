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

package com.wire.kalium.messagecontent

import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.history.HistoryClient
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import com.wire.kalium.logic.data.message.MessageAttachment
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageEncryptionAlgorithm
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.composite.CompositeButton
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewAsset
import com.wire.kalium.logic.data.message.linkpreview.MessageLinkPreview
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Attachment
import com.wire.kalium.protobuf.messages.Availability
import com.wire.kalium.protobuf.messages.CellAsset
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.Ephemeral
import com.wire.kalium.protobuf.messages.EncryptionAlgorithm
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.GenericMessage.UnknownStrategy
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import com.wire.kalium.protobuf.messages.LinkPreview
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.Mention
import com.wire.kalium.protobuf.messages.Multipart
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import com.wire.kalium.protobuf.messages.Text
import kotlinx.datetime.Instant
import com.wire.kalium.protobuf.messages.HistoryClient as ProtoHistoryClient

/**
 * Decodes GenericMessage protobufs into the existing shared application content model.
 *
 * This is deliberately receive-only. Encoding and all persistence/network behavior remain in the
 * full application graph.
 */
@Suppress("TooManyFunctions", "LargeClass", "ComplexMethod", "LongMethod")
public class ProtobufMessageContentDecoderImpl(
    private val selfUserId: UserId
) : ProtobufMessageContentDecoder {

    override fun decode(serializedContent: ByteArray): DecodedProtobufContent {
        val ownedBytes = serializedContent.copyOf()
        val genericMessage = GenericMessage.decodeFromByteArray(ownedBytes)
        val protoContent = when (val content = genericMessage.content) {
            is GenericMessage.Content.External -> ProtoContent.ExternalMessageInstructions(
                messageUid = genericMessage.messageId,
                otrKey = content.value.otrKey.array,
                sha256 = content.value.sha256?.array,
                encryptionAlgorithm = content.value.encryption.toMessageEncryptionAlgorithm()
            )

            else -> ProtoContent.Readable(
                messageUid = genericMessage.messageId,
                messageContent = getReadableContent(genericMessage, ownedBytes),
                expectsReadConfirmation = when (content) {
                    is GenericMessage.Content.Text -> content.value.expectsReadConfirmation ?: false
                    is GenericMessage.Content.Asset -> content.value.expectsReadConfirmation ?: false
                    else -> false
                },
                legalHoldStatus = getLegalHoldStatus(content).toModel(),
                expiresAfterMillis = (content as? GenericMessage.Content.Ephemeral)?.value?.expireAfterMillis
            )
        }
        val classification = when (genericMessage.content) {
            null -> DecodedProtobufContent.Classification.UNSUPPORTED
            is GenericMessage.Content.External -> DecodedProtobufContent.Classification.EXTERNAL_INSTRUCTIONS
            else -> DecodedProtobufContent.Classification.APPLICATION
        }
        return DecodedProtobufContent(ownedBytes, protoContent, classification)
    }

    private fun getLegalHoldStatus(content: GenericMessage.Content<*>?): LegalHoldStatus? = when (content) {
        is GenericMessage.Content.Text -> content.value.legalHoldStatus
        is GenericMessage.Content.Asset -> content.value.legalHoldStatus
        is GenericMessage.Content.Knock -> content.value.legalHoldStatus
        is GenericMessage.Content.Location -> content.value.legalHoldStatus
        is GenericMessage.Content.Reaction -> content.value.legalHoldStatus
        is GenericMessage.Content.Composite -> content.value.legalHoldStatus
        else -> null
    }

    private fun getReadableContent(
        genericMessage: GenericMessage,
        serializedContent: ByteArray
    ): MessageContent.FromProto = when (val content = genericMessage.content) {
        is GenericMessage.Content.Text -> unpackText(content.value)
        is GenericMessage.Content.Asset -> unpackAsset(content.value)
        is GenericMessage.Content.Availability -> MessageContent.Availability(content.value.toModel())
        is GenericMessage.Content.Composite -> unpackComposite(content)
        is GenericMessage.Content.ButtonAction -> MessageContent.ButtonAction(
            buttonId = content.value.buttonId,
            referencedMessageId = content.value.referenceMessageId
        )

        is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.ButtonActionConfirmation(
            referencedMessageId = content.value.referenceMessageId,
            buttonId = content.value.buttonId
        )

        is GenericMessage.Content.Calling -> MessageContent.Calling(
            value = content.value.content,
            conversationId = content.value.qualifiedConversationId?.toModel()
        )

        is GenericMessage.Content.Cleared -> MessageContent.Cleared(
            conversationId = extractConversationId(content.value.qualifiedConversationId, content.value.conversationId),
            time = Instant.fromEpochMilliseconds(content.value.clearedTimestamp),
            needToRemoveLocally = content.value.needToRemoveLocally ?: false
        )

        is GenericMessage.Content.ClientAction -> MessageContent.ClientAction
        is GenericMessage.Content.Confirmation -> unpackReceipt(content)
        is GenericMessage.Content.DataTransfer -> MessageContent.DataTransfer(
            content.value.trackingIdentifier?.let { MessageContent.DataTransfer.TrackingIdentifier(it.identifier) }
        )

        is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(content.value.messageId)
        is GenericMessage.Content.Edited -> unpackEdited(content, genericMessage)
        is GenericMessage.Content.Ephemeral -> unpackEphemeral(content.value)
        is GenericMessage.Content.Image -> MessageContent.Ignored
        is GenericMessage.Content.Hidden -> unpackHidden(genericMessage, content)
        is GenericMessage.Content.Knock -> MessageContent.Knock(content.value.hotKnock)
        is GenericMessage.Content.LastRead -> MessageContent.LastRead(
            messageId = genericMessage.messageId,
            conversationId = extractConversationId(content.value.qualifiedConversationId, content.value.conversationId),
            time = Instant.fromEpochMilliseconds(content.value.lastReadTimestamp)
        )

        is GenericMessage.Content.Location -> MessageContent.Location(
            latitude = content.value.latitude,
            longitude = content.value.longitude,
            name = content.value.name,
            zoom = content.value.zoom
        )

        is GenericMessage.Content.Reaction -> unpackReaction(content)
        is GenericMessage.Content.External -> MessageContent.Ignored
        is GenericMessage.Content.InCallEmoji -> MessageContent.InCallEmoji(
            content.value.emojis.mapNotNull {
                val key = it.key ?: return@mapNotNull null
                val value = it.value ?: return@mapNotNull null
                key to value
            }.associate { it }
        )

        is GenericMessage.Content.HistoryClientAvailable -> MessageContent.History.NewClientAvailable(
            content.value.client.toModel()
        )

        is GenericMessage.Content.HistoryClientRequest -> MessageContent.History.ClientsRequest
        is GenericMessage.Content.HistoryClientResponse -> MessageContent.History.ClientsResponse(
            content.value.clients.map { it.toModel() }
        )

        null -> when (genericMessage.unknownStrategy) {
            UnknownStrategy.DISCARD_AND_WARN -> MessageContent.Unknown()
            UnknownStrategy.WARN_USER_ALLOW_RETRY -> MessageContent.Unknown(encodedData = serializedContent.copyOf())
            UnknownStrategy.IGNORE,
            is UnknownStrategy.UNRECOGNIZED,
            null -> MessageContent.Ignored
        }

        is GenericMessage.Content.InCallHandRaise -> MessageContent.Ignored
        is GenericMessage.Content.Multipart -> unpackMultipart(content.value)
    }

    private fun unpackMultipart(content: Multipart): MessageContent.Multipart = MessageContent.Multipart(
        value = content.text?.content,
        linkPreviews = content.text?.linkPreview?.map(::mapLinkPreview) ?: emptyList(),
        mentions = content.text?.mentions?.mapNotNull(::mapMention) ?: emptyList(),
        quotedMessageReference = content.text?.toQuoteReference(),
        quotedMessageDetails = null,
        attachments = unpackMultipartAttachments(content.attachments)
    )

    private fun unpackMultipartAttachments(attachments: List<Attachment>): List<MessageAttachment> =
        attachments.mapNotNull { attachment ->
            when (val content = attachment.content) {
                is Attachment.Content.Asset -> null
                is Attachment.Content.CellAsset -> content.value.toModel()
                null -> null
            }
        }

    private fun unpackReceipt(content: GenericMessage.Content.Confirmation): MessageContent.FromProto {
        val type = when (content.value.type) {
            Confirmation.Type.DELIVERED -> ReceiptType.DELIVERED
            Confirmation.Type.READ -> ReceiptType.READ
            is Confirmation.Type.UNRECOGNIZED -> null
        }
        return type?.let {
            MessageContent.Receipt(it, listOf(content.value.firstMessageId) + content.value.moreMessageIds)
        } ?: MessageContent.Ignored
    }

    private fun unpackReaction(content: GenericMessage.Content.Reaction): MessageContent.Reaction {
        val emojiSet = content.value.emoji?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        return MessageContent.Reaction(content.value.messageId, emojiSet)
    }

    private fun unpackHidden(
        genericMessage: GenericMessage,
        content: GenericMessage.Content.Hidden
    ): MessageContent.Signaling = genericMessage.hidden?.let { hidden ->
        MessageContent.DeleteForMe(
            messageId = hidden.messageId,
            conversationId = extractConversationId(content.value.qualifiedConversationId, hidden.conversationId)
        )
    } ?: MessageContent.Ignored

    private fun unpackEdited(
        content: GenericMessage.Content.Edited,
        genericMessage: GenericMessage
    ): MessageContent.FromProto {
        val replacingMessageId = content.value.replacingMessageId
        return when (val editContent = content.value.content) {
            is MessageEdit.Content.Text -> MessageContent.TextEdited(
                editMessageId = replacingMessageId,
                newContent = editContent.value.content,
                newMentions = editContent.value.mentions.mapNotNull(::mapMention)
            )

            is MessageEdit.Content.Composite -> MessageContent.CompositeEdited(
                editMessageId = replacingMessageId,
                newTextContent = editContent.value.items.firstNotNullOfOrNull { it.text }?.let(::unpackText),
                newButtonList = unpackButtonList(editContent.value.items)
            )

            is MessageEdit.Content.Multipart -> {
                val editedText = editContent.value.text?.let(::unpackText)
                MessageContent.MultipartEdited(
                    editMessageId = replacingMessageId,
                    newTextContent = editedText?.value,
                    newAttachments = unpackMultipartAttachments(editContent.value.attachments),
                    newMentions = editedText?.mentions ?: emptyList()
                )
            }

            null -> {
                genericMessage.messageId // Keep the message available to a debugger without logging content here.
                MessageContent.Ignored
            }
        }
    }

    private fun unpackEphemeral(content: Ephemeral): MessageContent.FromProto = when (val ephemeral = content.content) {
        is Ephemeral.Content.Text -> unpackText(
            Text(
                content = ephemeral.value.content,
                mentions = ephemeral.value.mentions,
                quote = ephemeral.value.quote,
                expectsReadConfirmation = ephemeral.value.expectsReadConfirmation
            )
        )

        is Ephemeral.Content.Asset -> unpackAsset(
            Asset(
                original = ephemeral.value.original,
                status = ephemeral.value.status,
                expectsReadConfirmation = ephemeral.value.expectsReadConfirmation
            )
        )

        is Ephemeral.Content.Knock -> MessageContent.Knock(ephemeral.value.hotKnock)
        is Ephemeral.Content.Location -> MessageContent.Location(
            latitude = ephemeral.value.latitude,
            longitude = ephemeral.value.longitude,
            name = ephemeral.value.name,
            zoom = ephemeral.value.zoom
        )

        is Ephemeral.Content.Image,
        null -> MessageContent.Ignored
    }

    private fun unpackText(content: Text): MessageContent.Text = MessageContent.Text(
        value = content.content,
        linkPreviews = content.linkPreview.map(::mapLinkPreview),
        mentions = content.mentions.mapNotNull(::mapMention),
        quotedMessageReference = content.toQuoteReference(),
        quotedMessageDetails = null
    )

    private fun Text.toQuoteReference(): MessageContent.QuoteReference? = quote?.let {
        MessageContent.QuoteReference(
            quotedMessageId = it.quotedMessageId,
            quotedMessageSha256 = it.quotedMessageSha256?.array,
            isVerified = false
        )
    }

    private fun unpackAsset(content: Asset): MessageContent.Asset = MessageContent.Asset(content.toModel())

    private fun unpackComposite(content: GenericMessage.Content.Composite): MessageContent.Composite =
        MessageContent.Composite(
            textContent = content.value.items.firstNotNullOfOrNull { it.text }?.let(::unpackText),
            buttonList = unpackButtonList(content.value.items)
        )

    private fun unpackButtonList(items: List<com.wire.kalium.protobuf.messages.Composite.Item>): List<CompositeButton> =
        items.mapNotNull { item ->
            item.button?.let { button -> CompositeButton(button.text, button.id, isSelected = false) }
        }

    private fun extractConversationId(
        qualifiedConversationId: QualifiedConversationId?,
        unqualifiedConversationId: String
    ): ConversationId = qualifiedConversationId?.toModel()
        ?: ConversationId(unqualifiedConversationId, selfUserId.domain)

    private fun Mention.toUserId(): UserId? = qualifiedUserId?.let { UserId(it.id, it.domain) }
        ?: (mentionType as? Mention.MentionType.UserId)?.value?.let { UserId(it, selfUserId.domain) }

    private fun mapMention(mention: Mention): MessageMention? = mention.toUserId()?.let { userId ->
        MessageMention(
            start = mention.start,
            length = mention.length,
            userId = userId,
            isSelfMention = userId == selfUserId
        )
    }

    private fun mapLinkPreview(linkPreview: LinkPreview): MessageLinkPreview = MessageLinkPreview(
        url = linkPreview.url,
        urlOffset = linkPreview.urlOffset,
        permanentUrl = linkPreview.permanentUrl,
        title = linkPreview.title,
        summary = linkPreview.summary,
        image = linkPreview.image?.let { image ->
            LinkPreviewAsset(
                assetDataSize = image.original?.size ?: 0,
                mimeType = image.original?.mimeType ?: "*/*",
                assetName = image.original?.name,
                assetHeight = (image.original?.metaData as? Asset.Original.MetaData.Image)?.value?.height ?: 0,
                assetWidth = (image.original?.metaData as? Asset.Original.MetaData.Image)?.value?.width ?: 0,
                assetDataPath = null,
                assetToken = image.uploaded?.assetToken,
                assetDomain = image.uploaded?.assetDomain,
                assetKey = image.uploaded?.assetId
            )
        }
    )
}

private fun LegalHoldStatus?.toModel(): Conversation.LegalHoldStatus = when (this) {
    LegalHoldStatus.ENABLED -> Conversation.LegalHoldStatus.ENABLED
    LegalHoldStatus.DISABLED -> Conversation.LegalHoldStatus.DISABLED
    else -> Conversation.LegalHoldStatus.UNKNOWN
}

private fun EncryptionAlgorithm?.toMessageEncryptionAlgorithm(): MessageEncryptionAlgorithm? =
    when (this) {
        EncryptionAlgorithm.AES_CBC -> MessageEncryptionAlgorithm.AES_CBC
        EncryptionAlgorithm.AES_GCM -> MessageEncryptionAlgorithm.AES_GCM
        else -> null
    }

private fun Availability.toModel(): UserAvailabilityStatus = when (type) {
    Availability.Type.AVAILABLE -> UserAvailabilityStatus.AVAILABLE
    Availability.Type.BUSY -> UserAvailabilityStatus.BUSY
    Availability.Type.AWAY -> UserAvailabilityStatus.AWAY
    Availability.Type.NONE,
    is Availability.Type.UNRECOGNIZED -> UserAvailabilityStatus.NONE
}

private fun QualifiedConversationId.toModel(): ConversationId = ConversationId(id, domain)

private fun Asset.toModel(): AssetContent {
    val defaultRemoteData = AssetContent.RemoteData(
        otrKey = ByteArray(0),
        sha256 = ByteArray(0),
        assetId = "",
        assetDomain = null,
        assetToken = null,
        encryptionAlgorithm = null
    )
    return AssetContent(
        sizeInBytes = original?.size ?: 0,
        name = original?.name,
        mimeType = original?.mimeType ?: "*/*",
        metadata = when (val metadata = original?.metaData) {
            is Asset.Original.MetaData.Image -> AssetContent.AssetMetadata.Image(
                metadata.value.width,
                metadata.value.height
            )
            is Asset.Original.MetaData.Video -> AssetContent.AssetMetadata.Video(
                metadata.value.width,
                metadata.value.height,
                metadata.value.durationInMillis
            )

            is Asset.Original.MetaData.Audio -> AssetContent.AssetMetadata.Audio(
                metadata.value.durationInMillis,
                metadata.value.normalizedLoudness?.array
            )

            null -> null
        },
        remoteData = when (val uploadStatus = status) {
            is Asset.Status.Uploaded -> AssetContent.RemoteData(
                otrKey = uploadStatus.value.otrKey.array,
                sha256 = uploadStatus.value.sha256.array,
                assetId = uploadStatus.value.assetId ?: "",
                assetDomain = uploadStatus.value.assetDomain,
                assetToken = uploadStatus.value.assetToken,
                encryptionAlgorithm = uploadStatus.value.encryption.toMessageEncryptionAlgorithm()
            )

            is Asset.Status.NotUploaded,
            null -> defaultRemoteData
        }
    )
}

private fun CellAsset.toModel(): CellAssetContent = CellAssetContent(
    id = uuid,
    versionId = "",
    mimeType = contentType,
    assetPath = initialName,
    assetSize = initialSize,
    metadata = initialMetaData?.toModel(),
    transferStatus = AssetTransferStatus.NOT_DOWNLOADED
)

private fun CellAsset.InitialMetaData<*>.toModel(): AssetContent.AssetMetadata = when (this) {
    is CellAsset.InitialMetaData.Image -> AssetContent.AssetMetadata.Image(value.width, value.height)
    is CellAsset.InitialMetaData.Audio -> AssetContent.AssetMetadata.Audio(value.durationInMillis, null)
    is CellAsset.InitialMetaData.Video -> AssetContent.AssetMetadata.Video(
        value.width,
        value.height,
        value.durationInMillis
    )
}

private fun ProtoHistoryClient.toModel(): HistoryClient = HistoryClient(
    id = clientId,
    secret = HistoryClient.Secret(secret.array),
    creationTime = Instant.parse(createdAt)
)
