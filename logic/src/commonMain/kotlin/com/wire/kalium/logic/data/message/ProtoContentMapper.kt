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

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.asset.toProto
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.history.HistoryClient
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.composite.CompositeButton
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoder
import com.wire.kalium.messagecontent.ProtobufMessageContentDecoderImpl
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Attachment
import com.wire.kalium.protobuf.messages.Button
import com.wire.kalium.protobuf.messages.ButtonAction
import com.wire.kalium.protobuf.messages.ButtonActionConfirmation
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.CellAsset
import com.wire.kalium.protobuf.messages.Cleared
import com.wire.kalium.protobuf.messages.ClientAction
import com.wire.kalium.protobuf.messages.Composite
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.DataTransfer
import com.wire.kalium.protobuf.messages.Ephemeral
import com.wire.kalium.protobuf.messages.External
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.GenericMessage.Content.Availability
import com.wire.kalium.protobuf.messages.GenericMessage.Content.Deleted
import com.wire.kalium.protobuf.messages.HistoryClientAvailable
import com.wire.kalium.protobuf.messages.HistoryClientRequest
import com.wire.kalium.protobuf.messages.HistoryClientResponse
import com.wire.kalium.protobuf.messages.InCallEmoji
import com.wire.kalium.protobuf.messages.Knock
import com.wire.kalium.protobuf.messages.LastRead
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import com.wire.kalium.protobuf.messages.Location
import com.wire.kalium.protobuf.messages.MessageDelete
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.MessageHide
import com.wire.kalium.protobuf.messages.Multipart
import com.wire.kalium.protobuf.messages.Quote
import com.wire.kalium.protobuf.messages.Reaction
import com.wire.kalium.protobuf.messages.Text
import com.wire.kalium.protobuf.messages.TrackingIdentifier
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import pbandk.ByteArr
import com.wire.kalium.protobuf.messages.HistoryClient as ProtoHistoryClient

internal interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class ProtoContentMapperImpl(
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val availabilityMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = MapperProvider.linkPreviewMapper(),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
    private val messageContentDecoder: ProtobufMessageContentDecoder = ProtobufMessageContentDecoderImpl(selfUserId),
) : ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        val messageContent = when (protoContent) {
            is ProtoContent.ExternalMessageInstructions -> mapExternalMessageToProtobuf(protoContent)
            is ProtoContent.Readable -> mapReadableContentToProtobuf(protoContent)
        }

        val message = GenericMessage(
            messageId = protoContent.messageUid,
            content = messageContent
        )
        return PlainMessageBlob(message.encodeToByteArray())
    }

    @Suppress("ComplexMethod")
    private fun mapReadableContentToProtobuf(protoContent: ProtoContent.Readable): GenericMessage.Content<out Any> {
        val expiration = protoContent.expiresAfterMillis
        return if (expiration != null) {
            mapEphemeralContent(
                protoContent.messageContent,
                expiration,
                protoContent.expectsReadConfirmation,
                protoContent.legalHoldStatus
            )
        } else {
            mapNormalContent(
                protoContent.messageContent,
                protoContent.expectsReadConfirmation,
                protoContent.legalHoldStatus
            )
        }
    }

    @Suppress("ComplexMethod")
    private fun mapNormalContent(
        readableContent: MessageContent.FromProto,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content<out Any> {
        return when (readableContent) {
            is MessageContent.Text -> packText(readableContent, expectsReadConfirmation, legalHoldStatus)
            is MessageContent.Calling -> packCalling(readableContent)
            is MessageContent.Asset -> packAsset(readableContent, expectsReadConfirmation, legalHoldStatus)
            is MessageContent.Knock -> packKnock(readableContent, legalHoldStatus)
            is MessageContent.DeleteMessage -> Deleted(MessageDelete(messageId = readableContent.messageId))
            is MessageContent.DeleteForMe -> packHidden(readableContent)
            is MessageContent.Availability -> Availability(
                availabilityMapper.fromModelAvailabilityToProto(
                    readableContent.status
                )
            )

            is MessageContent.LastRead -> packLastRead(readableContent)
            is MessageContent.Cleared -> packCleared(readableContent)
            is MessageContent.Reaction -> packReaction(readableContent, legalHoldStatus)
            is MessageContent.Receipt -> packReceipt(readableContent)
            is MessageContent.ClientAction -> packClientAction()
            is MessageContent.TextEdited -> packEdited(readableContent)
            is MessageContent.MultipartEdited -> packEdited(readableContent)
            is MessageContent.CompositeEdited,
            is MessageContent.FailedDecryption,
            is MessageContent.RestrictedAsset,
            is MessageContent.Unknown, MessageContent.Ignored ->
                throw IllegalArgumentException(
                    "Unexpected message content type: ${readableContent.getType()}"
                )

            is MessageContent.Composite -> packComposite(readableContent, expectsReadConfirmation, legalHoldStatus)
            is MessageContent.ButtonAction -> packButtonAction(readableContent)

            is MessageContent.ButtonActionConfirmation -> packButtonActionConfirmation(readableContent)
            is MessageContent.Location -> packLocation(readableContent, expectsReadConfirmation, legalHoldStatus)

            is MessageContent.DataTransfer -> packDataTransfer(readableContent)
            is MessageContent.InCallEmoji -> packInCallEmoji(readableContent)
            is MessageContent.Multipart -> packMultipart(readableContent, expectsReadConfirmation, legalHoldStatus)
            is MessageContent.History -> packHistoryMessage(readableContent)
        }
    }

    private fun packHistoryMessage(readableContent: MessageContent.History): GenericMessage.Content<out Any> {
        fun mapHistoryClientToProto(historyClient: HistoryClient): ProtoHistoryClient {
            return ProtoHistoryClient(
                clientId = historyClient.id,
                createdAt = historyClient.creationTime.toIsoDateTimeString(),
                secret = ByteArr(historyClient.secret.value)
            )
        }
        return when (readableContent) {
            MessageContent.History.ClientsRequest -> GenericMessage.Content.HistoryClientRequest(HistoryClientRequest())
            is MessageContent.History.ClientsResponse -> GenericMessage.Content.HistoryClientResponse(
                HistoryClientResponse(readableContent.clients.map { mapHistoryClientToProto(it) })
            )

            is MessageContent.History.NewClientAvailable -> GenericMessage.Content.HistoryClientAvailable(
                HistoryClientAvailable(mapHistoryClientToProto(readableContent.client))
            )
        }
    }

    private fun packMultipart(
        readableContent: MessageContent.Multipart,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Multipart {
        val linkPreview = readableContent.linkPreviews.map { linkPreviewMapper.fromModelToProto(it) }
        val mentions = readableContent.mentions.map { messageMentionMapper.fromModelToProto(it) }
        val quote = readableContent.quotedMessageReference?.let {
            Quote(it.quotedMessageId, it.quotedMessageSha256?.let { hash -> ByteArr(hash) })
        }
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        val attachments = packMultipartAttachments(readableContent.attachments, expectsReadConfirmation, legalHoldStatus)

        return GenericMessage.Content.Multipart(
            Multipart(
                text = Text(
                    content = readableContent.value ?: "",
                    linkPreview = linkPreview,
                    mentions = mentions,
                    quote = quote,
                ),
                expectsReadConfirmation = expectsReadConfirmation,
                legalHoldStatus = protoLegalHoldStatus,
                attachments = attachments,
            )
        )
    }

    private fun packMultipartAttachments(
        attachments: List<MessageAttachment>,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): List<Attachment> = attachments.map { attachment ->
        when (attachment) {
            is AssetContent ->
                Attachment(
                    content = Attachment.Content.Asset(
                        asset = assetMapper.fromAssetContentToProtoAssetMessage(
                            messageContent = MessageContent.Asset(value = attachment),
                            expectsReadConfirmation = expectsReadConfirmation,
                            legalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus),
                        )
                    )
                )

            is CellAssetContent ->
                Attachment(
                    content = Attachment.Content.CellAsset(
                        cellAsset = CellAsset(
                            uuid = attachment.id,
                            contentType = attachment.mimeType,
                            initialName = attachment.assetPath,
                            initialSize = attachment.assetSize,
                            initialMetaData = attachment.metadata?.toProto()
                        )
                    )
                )
        }
    }

    private fun packLocation(
        readableContent: MessageContent.Location,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Location {
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        return GenericMessage.Content.Location(
            Location(
                latitude = readableContent.latitude,
                longitude = readableContent.longitude,
                name = readableContent.name,
                zoom = readableContent.zoom,
                expectsReadConfirmation = expectsReadConfirmation,
                legalHoldStatus = protoLegalHoldStatus
            )
        )
    }

    private fun packButtonAction(
        readableContent: MessageContent.ButtonAction
    ): GenericMessage.Content.ButtonAction =
        GenericMessage.Content.ButtonAction(
            ButtonAction(
                buttonId = readableContent.buttonId,
                referenceMessageId = readableContent.referencedMessageId
            )
        )

    private fun packButtonActionConfirmation(
        readableContent: MessageContent.ButtonActionConfirmation
    ): GenericMessage.Content.ButtonActionConfirmation =
        GenericMessage.Content.ButtonActionConfirmation(
            ButtonActionConfirmation(
                buttonId = readableContent.buttonId,
                referenceMessageId = readableContent.referencedMessageId
            )
        )

    private fun packComposite(
        readableContent: MessageContent.Composite,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Composite {
        val items: MutableList<Composite.Item> = mutableListOf()

        readableContent.textContent?.let {
            val text = packText(it, expectsReadConfirmation, legalHoldStatus)
            Composite.Item.Content.Text(text.value).also {
                items.add(Composite.Item(it))
            }
        }
        packButtonList(readableContent.buttonList).also {
            items.addAll(it)
        }

        val composite = GenericMessage.Content.Composite(
            Composite(
                items = items,
                expectsReadConfirmation = expectsReadConfirmation,
                legalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
            )
        )
        return composite
    }

    private fun mapEphemeralContent(
        readableContent: MessageContent.FromProto,
        expireAfterMillis: Long,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content<out Any> {
        val ephemeralContent = when (readableContent) {
            is MessageContent.Text -> {
                val text = packText(readableContent, expectsReadConfirmation, legalHoldStatus)
                Ephemeral.Content.Text(
                    text.value
                )
            }

            is MessageContent.Asset -> {
                val asset = packAsset(readableContent, expectsReadConfirmation, legalHoldStatus)
                Ephemeral.Content.Asset(
                    asset.value
                )
            }

            is MessageContent.Knock -> {
                val knock = packKnock(readableContent, legalHoldStatus)
                Ephemeral.Content.Knock(
                    knock.value
                )
            }

            is MessageContent.Location -> {
                val location = packLocation(readableContent, expectsReadConfirmation, legalHoldStatus)
                Ephemeral.Content.Location(
                    location.value
                )
            }

            is MessageContent.FailedDecryption,
            is MessageContent.RestrictedAsset,
            is MessageContent.Unknown,
            is MessageContent.Availability,
            is MessageContent.Calling,
            is MessageContent.Cleared,
            MessageContent.ClientAction,
            is MessageContent.DeleteForMe,
            is MessageContent.DeleteMessage,
            MessageContent.Ignored,
            is MessageContent.LastRead,
            is MessageContent.Reaction,
            is MessageContent.Receipt,
            is MessageContent.Composite,
            is MessageContent.ButtonAction,
            is MessageContent.ButtonActionConfirmation,
            is MessageContent.TextEdited,
            is MessageContent.CompositeEdited,
            is MessageContent.MultipartEdited,
            is MessageContent.DataTransfer,
            is MessageContent.InCallEmoji,
            is MessageContent.History,
            is MessageContent.Multipart -> throw IllegalArgumentException(
                "Unexpected message content type: ${readableContent.getType()}"
            )
        }
        return GenericMessage.Content.Ephemeral(Ephemeral(expireAfterMillis = expireAfterMillis, content = ephemeralContent))
    }

    private fun mapExternalMessageToProtobuf(protoContent: ProtoContent.ExternalMessageInstructions) =
        GenericMessage.Content.External(
            External(
                ByteArr(protoContent.otrKey),
                protoContent.sha256?.let { ByteArr(it) },
                protoContent.encryptionAlgorithm?.let {
                    encryptionAlgorithmMapper.toProtoBufModel(it)
                }
            )
        )

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        return messageContentDecoder.decode(encodedContent.data).content
    }

    private fun packReceipt(
        receiptContent: MessageContent.Receipt
    ): GenericMessage.Content.Confirmation {
        val firstMessage = receiptContent.messageIds.first()
        val restOfMessageIds = receiptContent.messageIds.drop(1)
        return GenericMessage.Content.Confirmation(
            Confirmation(
                type = when (receiptContent.type) {
                    ReceiptType.DELIVERED -> Confirmation.Type.DELIVERED
                    ReceiptType.READ -> Confirmation.Type.READ
                },
                firstMessageId = firstMessage,
                moreMessageIds = restOfMessageIds
            )
        )
    }

    private fun packReaction(
        readableContent: MessageContent.Reaction,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Reaction {
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        return GenericMessage.Content.Reaction(
            Reaction(
                emoji = readableContent.emojiSet.map { it.trim() }.filter { it.isNotBlank() }
                    .joinToString(separator = ",") { it },
                messageId = readableContent.messageId,
                legalHoldStatus = protoLegalHoldStatus
            )
        )
    }

    private fun packClientAction() = GenericMessage.Content.ClientAction(ClientAction.RESET_SESSION)

    private fun packLastRead(readableContent: MessageContent.LastRead) = GenericMessage.Content.LastRead(
        LastRead(
            conversationId = readableContent.conversationId.value,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            lastReadTimestamp = readableContent.time.toEpochMilliseconds()
        )
    )

    private fun packHidden(readableContent: MessageContent.DeleteForMe) = GenericMessage.Content.Hidden(
        MessageHide(
            messageId = readableContent.messageId,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            conversationId = readableContent.conversationId.value
        )
    )

    private fun packEdited(readableContent: MessageContent.TextEdited): GenericMessage.Content.Edited {
        val mentions = readableContent.newMentions.map { messageMentionMapper.fromModelToProto(it) }
        return GenericMessage.Content.Edited(
            MessageEdit(
                replacingMessageId = readableContent.editMessageId,
                content = MessageEdit.Content.Text( // TODO: for now we do not implement Composite
                    Text(
                        content = readableContent.newContent,
                        mentions = mentions,
                    )
                )
            )
        )
    }

    private fun packEdited(readableContent: MessageContent.MultipartEdited): GenericMessage.Content.Edited {
        val mentions = readableContent.newMentions.map { messageMentionMapper.fromModelToProto(it) }
        return GenericMessage.Content.Edited(
            MessageEdit(
                replacingMessageId = readableContent.editMessageId,
                content = MessageEdit.Content.Multipart(
                    Multipart(
                        text = Text(
                            content = readableContent.newTextContent ?: "",
                            mentions = mentions,
                        ),
                        attachments = packMultipartAttachments(readableContent.newAttachments, false, Conversation.LegalHoldStatus.UNKNOWN)
                    )
                )
            )
        )
    }

    private fun packCalling(readableContent: MessageContent.Calling) = GenericMessage.Content.Calling(
        Calling(
            content = readableContent.value,
            qualifiedConversationId = readableContent.conversationId?.let { idMapper.toProtoModel(it) }
        )
    )

    private fun packDataTransfer(readableContent: MessageContent.DataTransfer) = GenericMessage.Content.DataTransfer(
        DataTransfer(
            readableContent.trackingIdentifier?.identifier?.let { identifier ->
                TrackingIdentifier(identifier)
            }
        )
    )

    private fun packCleared(readableContent: MessageContent.Cleared) = GenericMessage.Content.Cleared(
        Cleared(
            conversationId = readableContent.conversationId.value,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            clearedTimestamp = readableContent.time.toEpochMilliseconds(),
            needToRemoveLocally = readableContent.needToRemoveLocally
        )
    )

    private fun toProtoLegalHoldStatus(legalHoldStatus: Conversation.LegalHoldStatus): LegalHoldStatus =
        when (legalHoldStatus) {
            Conversation.LegalHoldStatus.ENABLED -> LegalHoldStatus.ENABLED
            Conversation.LegalHoldStatus.DISABLED -> LegalHoldStatus.DISABLED
            else -> LegalHoldStatus.UNKNOWN
        }

    private fun packText(
        readableContent: MessageContent.Text,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Text {
        val linkPreview = readableContent.linkPreviews.map { linkPreviewMapper.fromModelToProto(it) }
        val mentions = readableContent.mentions.map { messageMentionMapper.fromModelToProto(it) }
        val quote = readableContent.quotedMessageReference?.let {
            Quote(it.quotedMessageId, it.quotedMessageSha256?.let { hash -> ByteArr(hash) })
        }
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        return GenericMessage.Content.Text(
            Text(
                content = readableContent.value,
                linkPreview = linkPreview,
                mentions = mentions,
                quote = quote,
                expectsReadConfirmation = expectsReadConfirmation,
                legalHoldStatus = protoLegalHoldStatus
            )
        )
    }

    private fun packButtonList(buttonList: List<CompositeButton>): List<Composite.Item> =
        buttonList.map {
            Composite.Item(
                Composite.Item.Content.Button(
                    button = Button(
                        text = it.text,
                        id = it.id
                    )
                )
            )
        }

    private fun packKnock(
        readableContent: MessageContent.Knock,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Knock {
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        return GenericMessage.Content.Knock(
            Knock(
                hotKnock = readableContent.hotKnock,
                legalHoldStatus = protoLegalHoldStatus
            )
        )
    }

    private fun packAsset(
        readableContent: MessageContent.Asset,
        expectsReadConfirmation: Boolean,
        legalHoldStatus: Conversation.LegalHoldStatus
    ): GenericMessage.Content.Asset {
        val protoLegalHoldStatus = toProtoLegalHoldStatus(legalHoldStatus)
        return GenericMessage.Content.Asset(
            asset = assetMapper.fromAssetContentToProtoAssetMessage(
                readableContent,
                expectsReadConfirmation,
                protoLegalHoldStatus
            )
        )
    }

    private fun packInCallEmoji(content: MessageContent.InCallEmoji): GenericMessage.Content.InCallEmoji {
        return GenericMessage.Content.InCallEmoji(
            inCallEmoji = InCallEmoji(
                emojis = content.emojis.map { entry ->
                    InCallEmoji.EmojisEntry(key = entry.key, value = entry.value)
                }
            )
        )
    }

}
