package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.mention.MessageMentionMapper
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.Cleared
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.External
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Knock
import com.wire.kalium.protobuf.messages.LastRead
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

@Suppress("TooManyFunctions")
class ProtoContentMapperImpl(
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val availabilityMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val selfUserId: UserId,
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

    private fun mapReadableContentToProtobuf(protoContent: ProtoContent.Readable) =
        when (val readableContent = protoContent.messageContent) {
            is MessageContent.Text -> packText(readableContent)

            is MessageContent.Calling -> GenericMessage.Content.Calling(Calling(content = readableContent.value))
            is MessageContent.Asset -> packAsset(readableContent)
            is MessageContent.Knock -> GenericMessage.Content.Knock(Knock(hotKnock = readableContent.hotKnock))
            is MessageContent.DeleteMessage -> GenericMessage.Content.Deleted(MessageDelete(messageId = readableContent.messageId))
            is MessageContent.DeleteForMe -> packHidden(readableContent)

            is MessageContent.Availability -> GenericMessage.Content.Availability(
                availabilityMapper.fromModelAvailabilityToProto(
                    readableContent.status
                )
            )

            is MessageContent.LastRead -> packLastRead(readableContent)

            is MessageContent.Cleared -> packCleared(readableContent)

            is MessageContent.Reaction -> packReaction(readableContent)

            is MessageContent.Receipt -> packReceipt(readableContent)

            is MessageContent.TextEdited -> TODO("Message type not yet supported")

            is MessageContent.FailedDecryption, is MessageContent.RestrictedAsset, is MessageContent.Unknown, MessageContent.Ignored ->
                throw IllegalArgumentException(
                    "Unexpected message content type: $readableContent"
                )
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
        protobufModel?.let {
            kaliumLogger.d(
                "Decoded message: {id:${genericMessage.messageId.obfuscateId()} ," + "content: ${it::class}}"
            )
        }

        return if (protobufModel is GenericMessage.Content.External) {
            val external = protobufModel.value
            val algorithm = encryptionAlgorithmMapper.fromProtobufModel(external.encryption)
            ProtoContent.ExternalMessageInstructions(genericMessage.messageId, external.otrKey.array, external.sha256?.array, algorithm)
        } else {
            ProtoContent.Readable(genericMessage.messageId, getReadableContent(genericMessage, encodedContent))
        }
    }

    @Suppress("ComplexMethod", "LongMethod")
    private fun getReadableContent(genericMessage: GenericMessage, encodedContent: PlainMessageBlob): MessageContent.FromProto {
        val typeName = genericMessage.content?.value?.let { it as? pbandk.Message }?.descriptor?.name

        val readableContent = when (val protoContent = genericMessage.content) {
            is GenericMessage.Content.Text -> unpackText(protoContent)

            is GenericMessage.Content.Asset -> unpackAsset(protoContent)

            is GenericMessage.Content.Availability -> MessageContent.Availability(
                availabilityMapper.fromProtoAvailabilityToModel(
                    protoContent.value
                )
            )

            is GenericMessage.Content.ButtonAction -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.Calling -> MessageContent.Calling(value = protoContent.value.content)
            is GenericMessage.Content.Cleared -> unpackCleared(protoContent)
            is GenericMessage.Content.ClientAction -> MessageContent.Ignored
            is GenericMessage.Content.Composite -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Confirmation -> unpackReceipt(protoContent)
            is GenericMessage.Content.DataTransfer -> MessageContent.Ignored
            is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(protoContent.value.messageId)
            is GenericMessage.Content.Edited -> unpackEdited(protoContent, typeName, encodedContent, genericMessage)
            is GenericMessage.Content.Ephemeral -> MessageContent.Ignored
            is GenericMessage.Content.Image -> MessageContent.Ignored // Deprecated in favor of GenericMessage.Content.Asset
            is GenericMessage.Content.Hidden -> unpackHidden(genericMessage, protoContent)
            is GenericMessage.Content.Knock -> MessageContent.Knock(protoContent.value.hotKnock)
            is GenericMessage.Content.LastRead -> unpackLastRead(genericMessage, protoContent)
            is GenericMessage.Content.Location -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Reaction -> unpackReaction(protoContent)

            else -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = $genericMessage.")
                MessageContent.Ignored
            }
        }
        return readableContent
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

    private fun packReaction(readableContent: MessageContent.Reaction) =
        GenericMessage.Content.Reaction(Reaction(emoji = readableContent.emojiSet.map { it.trim() }.filter { it.isNotBlank() }
            .joinToString(separator = ",") { it },
            messageId = readableContent.messageId
        )
        )

    private fun unpackReaction(protoContent: GenericMessage.Content.Reaction): MessageContent.Reaction {
        val emoji = protoContent.value.emoji
        val emojiSet = emoji?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
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

    private fun unpackEdited(
        protoContent: GenericMessage.Content.Edited,
        typeName: String?,
        encodedContent: PlainMessageBlob,
        genericMessage: GenericMessage
    ): MessageContent.FromProto {
        val replacingMessageId = protoContent.value.replacingMessageId
        return when (val editContent = protoContent.value.content) {
            is MessageEdit.Content.Text -> {
                val mentions = editContent.value.mentions.map { messageMentionMapper.fromProtoToModel(it) }
                MessageContent.TextEdited(
                    editMessageId = replacingMessageId, newContent = editContent.value.content, newMentions = mentions
                )
            }
            // TODO: for now we do not implement it
            is MessageEdit.Content.Composite -> {
                MessageContent.Unknown(typeName = typeName, encodedData = encodedContent.data)
            }

            null -> {
                kaliumLogger.w("Edit content is unexpected. Message UUID = $genericMessage.")
                MessageContent.Ignored
            }
        }
    }

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

    private fun packText(readableContent: MessageContent.Text): GenericMessage.Content.Text {
        val mentions = readableContent.mentions.map { messageMentionMapper.fromModelToProto(it) }
        val quote = readableContent.quotedMessageReference?.let {
            Quote(it.quotedMessageId, it.quotedMessageSha256?.let { hash -> ByteArr(hash) })
        }
        return GenericMessage.Content.Text(
            Text(
                content = readableContent.value,
                mentions = mentions,
                quote = quote,
                expectsReadConfirmation = readableContent.expectsReadConfirmation
            )
        )
    }

    private fun unpackText(protoContent: GenericMessage.Content.Text) = MessageContent.Text(
        value = protoContent.value.content,
        mentions = protoContent.value.mentions.map { messageMentionMapper.fromProtoToModel(it) },
        quotedMessageReference = protoContent.value.quote?.let {
            MessageContent.QuoteReference(
                quotedMessageId = it.quotedMessageId, quotedMessageSha256 = it.quotedMessageSha256?.array, isVerified = false
            )
        },
        quotedMessageDetails = null,
        expectsReadConfirmation = protoContent.value.expectsReadConfirmation ?: false
    )

    private fun packAsset(readableContent: MessageContent.Asset): GenericMessage.Content.Asset {
        return GenericMessage.Content.Asset(
            asset = assetMapper.fromAssetContentToProtoAssetMessage(
                readableContent
            )
        )
    }

    private fun unpackAsset(protoContent: GenericMessage.Content.Asset): MessageContent.Asset {
        // Backend sends some preview asset messages just with img metadata and no
        // keys or asset id,so we need to overwrite one with the other one
        return MessageContent.Asset(
            value = assetMapper.fromProtoAssetMessageToAssetContent(protoContent.value),
            expectsReadConfirmation = protoContent.value.expectsReadConfirmation ?: false
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
