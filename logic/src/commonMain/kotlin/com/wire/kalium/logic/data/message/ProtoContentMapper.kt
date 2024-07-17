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

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.linkpreview.LinkPreviewMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Asset
import com.wire.kalium.protobuf.messages.Button
import com.wire.kalium.protobuf.messages.ButtonAction
import com.wire.kalium.protobuf.messages.ButtonActionConfirmation
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.Cleared
import com.wire.kalium.protobuf.messages.ClientAction
import com.wire.kalium.protobuf.messages.Composite
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.Ephemeral
import com.wire.kalium.protobuf.messages.External
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Knock
import com.wire.kalium.protobuf.messages.LastRead
import com.wire.kalium.protobuf.messages.LegalHoldStatus
import com.wire.kalium.protobuf.messages.Location
import com.wire.kalium.protobuf.messages.MessageDelete
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.MessageHide
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import com.wire.kalium.protobuf.messages.Quote
import com.wire.kalium.protobuf.messages.Reaction
import com.wire.kalium.protobuf.messages.Text
import kotlinx.datetime.Instant
import pbandk.ByteArr

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
class ProtoContentMapperImpl(
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val availabilityMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val selfUserId: UserId,
    private val linkPreviewMapper: LinkPreviewMapper = MapperProvider.linkPreviewMapper(),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(selfUserId),
) : ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        val messageContent = when (protoContent) {
            is ProtoContent.ExternalMessageInstructions -> mapExternalMessageToProtobuf(protoContent)
            is ProtoContent.Readable -> mapReadableContentToProtobuf(protoContent)
        }

        val message = GenericMessage(protoContent.messageUid, messageContent)
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
            is MessageContent.DeleteMessage -> GenericMessage.Content.Deleted(MessageDelete(messageId = readableContent.messageId))
            is MessageContent.DeleteForMe -> packHidden(readableContent)
            is MessageContent.Availability -> GenericMessage.Content.Availability(
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
            is MessageContent.FailedDecryption, is MessageContent.RestrictedAsset, is MessageContent.Unknown, MessageContent.Ignored ->
                throw IllegalArgumentException(
                    "Unexpected message content type: ${readableContent.getType()}"
                )

            is MessageContent.Composite -> packComposite(readableContent, expectsReadConfirmation, legalHoldStatus)
            is MessageContent.ButtonAction -> packButtonAction(readableContent)

            is MessageContent.ButtonActionConfirmation -> packButtonActionConfirmation(readableContent)
            is MessageContent.Location -> packLocation(readableContent, expectsReadConfirmation, legalHoldStatus)

            is MessageContent.DataTransfer -> TODO("Analytics: Not yet implemented")
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
            is MessageContent.DataTransfer -> throw IllegalArgumentException(
                "Unexpected message content type: ${readableContent.getType()}"
            )
        }
        return GenericMessage.Content.Ephemeral(Ephemeral(expireAfterMillis = expireAfterMillis, content = ephemeralContent))
    }

    private fun mapExternalMessageToProtobuf(protoContent: ProtoContent.ExternalMessageInstructions) =
        GenericMessage.Content.External(
            External(ByteArr(protoContent.otrKey),
                protoContent.sha256?.let { ByteArr(it) },
                protoContent.encryptionAlgorithm?.let { encryptionAlgorithmMapper.toProtoBufModel(it) })
        )

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.decodeFromByteArray(encodedContent.data)
        val protobufModel = genericMessage.content

        return if (protobufModel is GenericMessage.Content.External) {
            val external = protobufModel.value
            val algorithm = encryptionAlgorithmMapper.fromProtobufModel(external.encryption)
            ProtoContent.ExternalMessageInstructions(genericMessage.messageId, external.otrKey.array, external.sha256?.array, algorithm)
        } else {
            val expectsReadConfirmation = when (val content = genericMessage.content) {
                is GenericMessage.Content.Text -> content.value.expectsReadConfirmation ?: false
                is GenericMessage.Content.Asset -> content.value.expectsReadConfirmation ?: false
                else -> false
            }
            val legalHoldStatus = getLegalHoldStatusFromProtoContent(genericMessage)
            val expiresAfterMillis: Long? = when (val content = genericMessage.content) {
                is GenericMessage.Content.Ephemeral -> content.value.expireAfterMillis
                else -> null
            }
            ProtoContent.Readable(
                messageUid = genericMessage.messageId,
                messageContent = getReadableContent(genericMessage, encodedContent),
                expectsReadConfirmation = expectsReadConfirmation,
                legalHoldStatus = fromProtoLegalHoldStatus(legalHoldStatus),
                expiresAfterMillis = expiresAfterMillis
            )
        }
    }

    private fun getLegalHoldStatusFromProtoContent(genericMessage: GenericMessage) =
        when (val content = genericMessage.content) {
            is GenericMessage.Content.Text -> content.value.legalHoldStatus
            is GenericMessage.Content.Asset -> content.value.legalHoldStatus
            is GenericMessage.Content.Knock -> content.value.legalHoldStatus
            is GenericMessage.Content.Location -> content.value.legalHoldStatus
            is GenericMessage.Content.Reaction -> content.value.legalHoldStatus
            is GenericMessage.Content.Composite -> content.value.legalHoldStatus
            else -> null
        }

    private fun fromProtoLegalHoldStatus(legalHoldStatus: LegalHoldStatus?): Conversation.LegalHoldStatus =
        legalHoldStatus?.let {
            when (legalHoldStatus) {
                is LegalHoldStatus.ENABLED -> Conversation.LegalHoldStatus.ENABLED
                is LegalHoldStatus.DISABLED -> Conversation.LegalHoldStatus.DISABLED
                else -> Conversation.LegalHoldStatus.UNKNOWN
            }
        } ?: run { Conversation.LegalHoldStatus.UNKNOWN }

    @Suppress("ComplexMethod", "LongMethod")
    private fun getReadableContent(
        genericMessage: GenericMessage,
        encodedContent: PlainMessageBlob
    ): MessageContent.FromProto {
        val typeName = genericMessage.content?.value?.let { it as? pbandk.Message }?.descriptor?.name

        val readableContent = when (val protoContent = genericMessage.content) {
            is GenericMessage.Content.Text -> unpackText(protoContent.value)
            is GenericMessage.Content.Asset -> unpackAsset(protoContent)
            is GenericMessage.Content.Availability -> MessageContent.Availability(
                availabilityMapper.fromProtoAvailabilityToModel(
                    protoContent.value
                )
            )

            is GenericMessage.Content.Composite -> unpackComposite(protoContent)

            is GenericMessage.Content.ButtonAction -> MessageContent.ButtonAction(
                buttonId = protoContent.value.buttonId,
                referencedMessageId = protoContent.value.referenceMessageId
            )

            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.ButtonActionConfirmation(
                referencedMessageId = protoContent.value.referenceMessageId,
                buttonId = protoContent.value.buttonId
            )

            is GenericMessage.Content.Calling -> unpackCalling(protoContent)
            is GenericMessage.Content.Cleared -> unpackCleared(protoContent)
            is GenericMessage.Content.ClientAction -> MessageContent.ClientAction
            is GenericMessage.Content.Confirmation -> unpackReceipt(protoContent)
            is GenericMessage.Content.DataTransfer -> unpackDataTransfer(protoContent)
            is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(protoContent.value.messageId)
            is GenericMessage.Content.Edited -> unpackEdited(protoContent, typeName, encodedContent, genericMessage)
            is GenericMessage.Content.Ephemeral -> unpackEphemeral(protoContent)
            is GenericMessage.Content.Image -> MessageContent.Ignored // Deprecated in favor of GenericMessage.Content.Asset
            is GenericMessage.Content.Hidden -> unpackHidden(genericMessage, protoContent)
            is GenericMessage.Content.Knock -> MessageContent.Knock(protoContent.value.hotKnock)
            is GenericMessage.Content.LastRead -> unpackLastRead(genericMessage, protoContent)
            is GenericMessage.Content.Location -> unpackLocation(protoContent)
            is GenericMessage.Content.Reaction -> unpackReaction(protoContent)

            is GenericMessage.Content.External -> {
                kaliumLogger.w("External content when parsing protobuf. Message UUID = ${genericMessage.messageId.obfuscateId()}")
                MessageContent.Ignored
            }

            null -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = ${genericMessage.messageId.obfuscateId()}")
                MessageContent.Ignored
            }
        }
        return readableContent
    }

    private fun unpackLocation(
        protoContent: GenericMessage.Content.Location
    ): MessageContent.FromProto = MessageContent.Location(
        latitude = protoContent.value.latitude,
        longitude = protoContent.value.longitude,
        name = protoContent.value.name,
        zoom = protoContent.value.zoom
    )

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
                }, firstMessageId = firstMessage, moreMessageIds = restOfMessageIds
            )
        )
    }

    private fun unpackReceipt(
        protoContent: GenericMessage.Content.Confirmation
    ): MessageContent.FromProto = when (val protoType = protoContent.value.type) {
        Confirmation.Type.DELIVERED -> ReceiptType.DELIVERED
        Confirmation.Type.READ -> ReceiptType.READ
        is Confirmation.Type.UNRECOGNIZED -> {
            kaliumLogger.w("Unrecognised receipt type received = ${protoType.value}:${protoType.name}")
            null
        }
    }?.let { type ->
        MessageContent.Receipt(
            type = type, messageIds = listOf(protoContent.value.firstMessageId) + protoContent.value.moreMessageIds
        )
    } ?: MessageContent.Ignored

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

    private fun unpackReaction(protoContent: GenericMessage.Content.Reaction): MessageContent.Reaction {
        val emoji = protoContent.value.emoji
        val emojiSet = emoji?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: emptySet()
        return MessageContent.Reaction(protoContent.value.messageId, emojiSet)
    }

    private fun packLastRead(readableContent: MessageContent.LastRead) = GenericMessage.Content.LastRead(
        LastRead(
            conversationId = readableContent.conversationId.value,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            lastReadTimestamp = readableContent.time.toEpochMilliseconds()
        )
    )

    private fun unpackLastRead(
        genericMessage: GenericMessage,
        protoContent: GenericMessage.Content.LastRead
    ) = MessageContent.LastRead(
        messageId = genericMessage.messageId,
        conversationId = extractConversationId(protoContent.value.qualifiedConversationId, protoContent.value.conversationId),
        time = Instant.fromEpochMilliseconds(protoContent.value.lastReadTimestamp)
    )

    private fun packHidden(readableContent: MessageContent.DeleteForMe) = GenericMessage.Content.Hidden(
        MessageHide(
            messageId = readableContent.messageId,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            conversationId = readableContent.conversationId.value
        )
    )

    private fun unpackHidden(
        genericMessage: GenericMessage,
        protoContent: GenericMessage.Content.Hidden
    ): MessageContent.Signaling {
        val hiddenMessage = genericMessage.hidden
        return if (hiddenMessage != null) {
            MessageContent.DeleteForMe(
                messageId = hiddenMessage.messageId,
                conversationId = extractConversationId(protoContent.value.qualifiedConversationId, hiddenMessage.conversationId),
            )
        } else {
            kaliumLogger.w("Hidden message is null. Message UUID = $genericMessage.")
            MessageContent.Ignored
        }
    }

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

    private fun unpackEdited(
        protoContent: GenericMessage.Content.Edited,
        typeName: String?,
        encodedContent: PlainMessageBlob,
        genericMessage: GenericMessage
    ): MessageContent.FromProto {
        val replacingMessageId = protoContent.value.replacingMessageId
        return when (val editContent = protoContent.value.content) {
            is MessageEdit.Content.Text -> {
                val mentions = editContent.value.mentions.mapNotNull { messageMentionMapper.fromProtoToModel(it) }
                MessageContent.TextEdited(
                    editMessageId = replacingMessageId, newContent = editContent.value.content, newMentions = mentions
                )
            }
            // TODO: for now we do not implement it
            is MessageEdit.Content.Composite -> {
                MessageContent.Unknown(typeName = typeName, encodedData = encodedContent.data)
            }

            null -> {
                kaliumLogger.w("Edit content is unexpected. Message UUID = ${genericMessage.messageId.obfuscateId()}")
                MessageContent.Ignored
            }
        }
    }

    private fun packCalling(readableContent: MessageContent.Calling) = GenericMessage.Content.Calling(
        Calling(
            content = readableContent.value,
            qualifiedConversationId = readableContent.conversationId?.let { idMapper.toProtoModel(it) }
        )
    )

    private fun unpackCalling(protoContent: GenericMessage.Content.Calling) = MessageContent.Calling(
        value = protoContent.value.content,
        conversationId = protoContent.value.qualifiedConversationId?.let { idMapper.fromProtoModel(it) }
    )

    private fun unpackDataTransfer(protoContent: GenericMessage.Content.DataTransfer) = MessageContent.DataTransfer(
        trackingIdentifier = protoContent.value.trackingIdentifier?.let { trackingIdentifier ->
            MessageContent.DataTransfer.TrackingIdentifier(
                identifier = trackingIdentifier.identifier
            )
        }
    )

    private fun packCleared(readableContent: MessageContent.Cleared) = GenericMessage.Content.Cleared(
        Cleared(
            conversationId = readableContent.conversationId.value,
            qualifiedConversationId = idMapper.toProtoModel(readableContent.conversationId),
            clearedTimestamp = readableContent.time.toEpochMilliseconds()
        )
    )

    private fun unpackCleared(protoContent: GenericMessage.Content.Cleared) = MessageContent.Cleared(
        conversationId = extractConversationId(protoContent.value.qualifiedConversationId, protoContent.value.conversationId),
        time = Instant.fromEpochMilliseconds(protoContent.value.clearedTimestamp)
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

    private fun packButtonList(buttonList: List<MessageContent.Composite.Button>): List<Composite.Item> = buttonList.map {
        Composite.Item(
            Composite.Item.Content.Button(
                button = Button(
                    text = it.text,
                    id = it.id
                )
            )
        )
    }

    private fun unpackText(protoContent: Text) = MessageContent.Text(
        value = protoContent.content,
        linkPreviews = protoContent.linkPreview.mapNotNull { linkPreviewMapper.fromProtoToModel(it) },
        mentions = protoContent.mentions.mapNotNull { messageMentionMapper.fromProtoToModel(it) },
        quotedMessageReference = protoContent.quote?.let {
            MessageContent.QuoteReference(
                quotedMessageId = it.quotedMessageId, quotedMessageSha256 = it.quotedMessageSha256?.array, isVerified = false
            )
        },
        quotedMessageDetails = null
    )

    private fun unpackButtonList(compositeItemList: List<Composite.Item>): List<MessageContent.Composite.Button> =
        compositeItemList.mapNotNull {
            it.button?.let { button ->
                MessageContent.Composite.Button(
                    text = button.text,
                    id = button.id,
                    isSelected = false
                )
            }
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

    private fun unpackEphemeral(
        protoContent: GenericMessage.Content.Ephemeral
    ): MessageContent.FromProto {
        val messageContent = when (val ephemeralContent = protoContent.value.content) {
            is Ephemeral.Content.Text -> {
                val genericMessageTextContent = GenericMessage.Content.Text(
                    Text(
                        content = ephemeralContent.value.content,
                        mentions = ephemeralContent.value.mentions,
                        quote = ephemeralContent.value.quote,
                        expectsReadConfirmation = ephemeralContent.value.expectsReadConfirmation
                    )
                )
                unpackText(genericMessageTextContent.value)
            }

            is Ephemeral.Content.Asset -> {
                val genericAssetContent = GenericMessage.Content.Asset(
                    Asset(
                        original = ephemeralContent.value.original,
                        status = ephemeralContent.value.status,
                        expectsReadConfirmation = ephemeralContent.value.expectsReadConfirmation
                    )
                )
                unpackAsset(genericAssetContent)
            }

            is Ephemeral.Content.Knock -> {
                MessageContent.Knock(
                    ephemeralContent.value.hotKnock
                )
            }

            is Ephemeral.Content.Location -> {
                val location = GenericMessage.Content.Location(
                    Location(
                        latitude = ephemeralContent.value.latitude,
                        longitude = ephemeralContent.value.longitude,
                        name = ephemeralContent.value.name,
                        zoom = ephemeralContent.value.zoom
                    )
                )
                unpackLocation(location)
            }

            // Handle self-deleting Location messages when they are implemented
            is Ephemeral.Content.Image,
            null -> {
                MessageContent.Ignored
            }
        }

        return messageContent
    }

    private fun unpackAsset(protoContent: GenericMessage.Content.Asset): MessageContent.Asset {
        // Backend sends some preview asset messages just with img metadata and no
        // keys or asset id,so we need to overwrite one with the other one
        return MessageContent.Asset(
            value = assetMapper.fromProtoAssetMessageToAssetContent(protoContent.value)
        )
    }

    private fun unpackComposite(protoContent: GenericMessage.Content.Composite): MessageContent.Composite {
        val text = protoContent.value.items.firstNotNullOfOrNull { item ->
            item.text
        }?.let(::unpackText)
        val buttonList = unpackButtonList(protoContent.value.items)

        return MessageContent.Composite(
            textContent = text,
            buttonList = buttonList
        )
    }

    private fun extractConversationId(
        qualifiedConversationID: QualifiedConversationId?,
        unqualifiedConversationID: String
    ): ConversationId {
        return if (qualifiedConversationID != null) idMapper.fromProtoModel(qualifiedConversationID)
        else ConversationId(unqualifiedConversationID, selfUserId.domain)
    }
}
