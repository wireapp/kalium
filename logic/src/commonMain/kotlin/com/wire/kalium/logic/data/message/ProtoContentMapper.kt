package com.wire.kalium.logic.data.message

import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.user.AvailabilityStatusMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.Confirmation
import com.wire.kalium.protobuf.messages.Cleared
import com.wire.kalium.protobuf.messages.External
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.Knock
import com.wire.kalium.protobuf.messages.LastRead
import com.wire.kalium.protobuf.messages.MessageDelete
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.MessageHide
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import com.wire.kalium.protobuf.messages.Reaction
import com.wire.kalium.protobuf.messages.Text
import kotlinx.datetime.Instant
import pbandk.ByteArr

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

class ProtoContentMapperImpl(
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val availabilityMapper: AvailabilityStatusMapper = MapperProvider.availabilityStatusMapper(),
    private val confirmationTypeMapper: ConfirmationTypeMapper = MapperProvider.confirmationTypeMapper(),
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val messageMentionMapper: MessageMentionMapper = MapperProvider.messageMentionMapper(),
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
    private fun mapReadableContentToProtobuf(protoContent: ProtoContent.Readable) =
        when (val readableContent = protoContent.messageContent) {
            is MessageContent.Text -> GenericMessage.Content.Text(Text(content = readableContent.value))
            is MessageContent.Confirmation -> GenericMessage.Content.Confirmation(
                Confirmation(
                    type = confirmationTypeMapper.fromModelConfirmationTypeToProto(readableContent.type),
                    firstMessageId = readableContent.firstMessageId,
                    moreMessageIds = readableContent.moreMessageIds
                )
            )
            is MessageContent.Calling -> GenericMessage.Content.Calling(Calling(content = readableContent.value))
            is MessageContent.Asset -> GenericMessage.Content.Asset(assetMapper.fromAssetContentToProtoAssetMessage(readableContent.value))
            is MessageContent.Knock -> GenericMessage.Content.Knock(Knock(hotKnock = readableContent.hotKnock))
            is MessageContent.DeleteMessage -> GenericMessage.Content.Deleted(MessageDelete(messageId = readableContent.messageId))
            is MessageContent.DeleteForMe -> GenericMessage.Content.Hidden(
                MessageHide(
                    messageId = readableContent.messageId,
                    qualifiedConversationId = readableContent.conversationId?.let { idMapper.toProtoModel(it) },
                    conversationId = readableContent.unqualifiedConversationId
                )
            )

            is MessageContent.Availability ->
                GenericMessage.Content.Availability(availabilityMapper.fromModelAvailabilityToProto(readableContent.status))

            is MessageContent.LastRead -> {
                GenericMessage.Content.LastRead(
                    LastRead(
                        conversationId = readableContent.unqualifiedConversationId,
                        qualifiedConversationId = readableContent.conversationId?.let { idMapper.toProtoModel(it) },
                        lastReadTimestamp = readableContent.time.toEpochMilliseconds()
                    )
                )
            }

            is MessageContent.Cleared -> {
                GenericMessage.Content.Cleared(
                    Cleared(
                        conversationId = readableContent.unqualifiedConversationId,
                        qualifiedConversationId = readableContent.conversationId?.let { idMapper.toProtoModel(it) },
                        clearedTimestamp = readableContent.time.toEpochMilliseconds()
                    )
                )
            }

            is MessageContent.Reaction -> {
                GenericMessage.Content.Reaction(
                    Reaction(
                        emoji = readableContent.emojiSet.joinToString(separator = ",") { it },
                        messageId = readableContent.messageId
                    )
                )
            }

            else -> throw IllegalArgumentException("Unexpected message content type: $readableContent")
        }

    private fun mapExternalMessageToProtobuf(protoContent: ProtoContent.ExternalMessageInstructions) =
        GenericMessage.Content.External(
            External(
                ByteArr(protoContent.otrKey),
                protoContent.sha256?.let { ByteArr(it) },
                protoContent.encryptionAlgorithm?.let { encryptionAlgorithmMapper.toProtoBufModel(it) }
            )
        )

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.decodeFromByteArray(encodedContent.data)
        val protobufModel = genericMessage.content
        protobufModel?.let {
            kaliumLogger.d(
                "Decoded message: {id:${genericMessage.messageId.obfuscateId()} ," +
                        "content: ${it::class}}"
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
            is GenericMessage.Content.Text -> MessageContent.Text(
                protoContent.value.content,
                protoContent.value.mentions.map { messageMentionMapper.fromProtoToModel(it) }.filterNotNull()
            )

            is GenericMessage.Content.Asset -> {
                // Backend sends some preview asset messages just with img metadata and no keys or asset id, so we need to overwrite one with the other one
                MessageContent.Asset(assetMapper.fromProtoAssetMessageToAssetContent(protoContent.value))
            }

            is GenericMessage.Content.Availability ->
                MessageContent.Availability(availabilityMapper.fromProtoAvailabilityToModel(protoContent.value))

            is GenericMessage.Content.ButtonAction -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.Calling -> MessageContent.Calling(value = protoContent.value.content)
            is GenericMessage.Content.Cleared -> {
                MessageContent.Cleared(
                    unqualifiedConversationId = protoContent.value.conversationId,
                    conversationId = extractConversationId(protoContent.value.qualifiedConversationId),
                    time = Instant.fromEpochMilliseconds(protoContent.value.clearedTimestamp)
                )
            }

            is GenericMessage.Content.ClientAction -> MessageContent.Ignored
            is GenericMessage.Content.Composite -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Confirmation -> MessageContent.Confirmation(
                confirmationTypeMapper.fromProtoConfirmationTypeToModel(protoContent.value.type),
                protoContent.value.firstMessageId,
                protoContent.value.moreMessageIds
            )
            is GenericMessage.Content.DataTransfer -> MessageContent.Ignored
            is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(protoContent.value.messageId)
            is GenericMessage.Content.Edited -> {
                val replacingMessageId = protoContent.value.replacingMessageId
                when (val editContent = protoContent.value.content) {
                    is MessageEdit.Content.Text -> {
                        MessageContent.TextEdited(
                            replacingMessageId,
                            editContent.value.content,
                            editContent.value.mentions.map { messageMentionMapper.fromProtoToModel(it) }.filterNotNull()
                        )
                    }
                    // TODO: for now we do not implement it
                    is MessageEdit.Content.Composite -> {
                        MessageContent.Unknown(typeName, encodedContent.data)
                    }

                    null -> {
                        kaliumLogger.w("Edit content is unexpected. Message UUID = $genericMessage.")
                        MessageContent.Ignored
                    }
                }
            }

            is GenericMessage.Content.Ephemeral -> MessageContent.Ignored
            is GenericMessage.Content.Image -> MessageContent.Ignored // Deprecated in favor of GenericMessage.Content.Asset
            is GenericMessage.Content.Hidden -> {
                val hiddenMessage = genericMessage.hidden
                if (hiddenMessage != null) {
                    MessageContent.DeleteForMe(
                        messageId = hiddenMessage.messageId,
                        unqualifiedConversationId = hiddenMessage.conversationId,
                        conversationId = extractConversationId(protoContent.value.qualifiedConversationId),
                    )
                } else {
                    kaliumLogger.w("Hidden message is null. Message UUID = $genericMessage.")
                    MessageContent.Ignored
                }
            }

            is GenericMessage.Content.Knock -> MessageContent.Knock(protoContent.value.hotKnock)
            is GenericMessage.Content.LastRead -> {
                MessageContent.LastRead(
                    messageId = genericMessage.messageId,
                    unqualifiedConversationId = protoContent.value.conversationId,
                    conversationId = extractConversationId(protoContent.value.qualifiedConversationId),
                    time = Instant.fromEpochMilliseconds(protoContent.value.lastReadTimestamp)
                )
            }

            is GenericMessage.Content.Location -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Reaction -> {
                val emoji = protoContent.value.emoji
                // TODO: Actually handle Unicode properly
                // We need to filter out the unicode variants for the emojis
                val emojiSet = emoji?.split(',')?.filter { it.isNotBlank() }
                    ?.toSet() ?: emptySet()
                MessageContent.Reaction(protoContent.value.messageId, emojiSet)
            }

            else -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = $genericMessage.")
                MessageContent.Ignored
            }
        }
        return readableContent
    }

    private fun extractConversationId(qualifiedConversationID: QualifiedConversationId?): ConversationId? {
        return if (qualifiedConversationID != null)
            idMapper.fromProtoModel(qualifiedConversationID)
        else null
    }
}
