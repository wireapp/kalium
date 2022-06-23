package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.asset.AssetMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.MessageDelete
import com.wire.kalium.protobuf.messages.MessageEdit
import com.wire.kalium.protobuf.messages.MessageHide
import com.wire.kalium.protobuf.messages.Text

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

class ProtoContentMapperImpl(
    private val assetMapper: AssetMapper = MapperProvider.assetMapper(),
    private val encryptionAlgorithmMapper: EncryptionAlgorithmMapper = MapperProvider.encryptionAlgorithmMapper()
) : ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        if (protoContent !is ProtoContent.Readable) TODO("External message encoding not yet implemented")

        val (messageUid, messageContent) = protoContent

        val content = when (messageContent) {
            //TODO maybe here
            is MessageContent.Text -> GenericMessage.Content.Text(Text(content = messageContent.value))
            is MessageContent.Calling -> GenericMessage.Content.Calling(Calling(content = messageContent.value))
            is MessageContent.Asset -> GenericMessage.Content.Asset(assetMapper.fromAssetContentToProtoAssetMessage(messageContent.value))
            is MessageContent.DeleteMessage -> GenericMessage.Content.Deleted(MessageDelete(messageId = messageContent.messageId))
            is MessageContent.DeleteForMe -> GenericMessage.Content.Hidden(
                MessageHide(
                    messageId = messageContent.messageId,
                    conversationId = messageContent.conversationId,
                    qualifiedConversationId = messageContent.qualifiedConversationId
                )
            )

            else -> throw IllegalArgumentException("Unexpected message content type: $messageContent")
        }

        val message = GenericMessage(messageUid, content)
        return PlainMessageBlob(message.encodeToByteArray())
    }

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.decodeFromByteArray(encodedContent.data)

        kaliumLogger.d("Received message $genericMessage")
        val protobufModel = genericMessage.content

        return if (protobufModel is GenericMessage.Content.External) {
            val external = protobufModel.value
            val algorithm = encryptionAlgorithmMapper.fromProtobufModel(external.encryption)
            ProtoContent.ExternalMessageInstructions(genericMessage.messageId, external.otrKey.array, external.sha256?.array, algorithm)
        } else {
            ProtoContent.Readable(genericMessage.messageId, getReadableContent(genericMessage, encodedContent))
        }
    }

    @Suppress("ComplexMethod")
    private fun getReadableContent(genericMessage: GenericMessage, encodedContent: PlainMessageBlob): MessageContent.FromProto {
        val typeName = genericMessage.content?.value?.let { it as? pbandk.Message }?.descriptor?.name

        val readableContent = when (val protoContent = genericMessage.content) {
            is GenericMessage.Content.Text -> MessageContent.Text(protoContent.value.content)
            is GenericMessage.Content.Asset -> {
                // Backend sends some preview asset messages just with img metadata and no keys or asset id, so we need to overwrite one with the other one
                MessageContent.Asset(assetMapper.fromProtoAssetMessageToAssetContent(protoContent.value))
            }

            is GenericMessage.Content.Availability -> MessageContent.Ignored
            is GenericMessage.Content.ButtonAction -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.Unknown(typeName, encodedContent.data, true)
            is GenericMessage.Content.Calling -> MessageContent.Calling(value = protoContent.value.content)
            is GenericMessage.Content.Cleared -> MessageContent.Ignored
            is GenericMessage.Content.ClientAction -> MessageContent.Ignored
            is GenericMessage.Content.Composite -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Confirmation -> MessageContent.Ignored
            is GenericMessage.Content.DataTransfer -> MessageContent.Ignored
            is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(protoContent.value.messageId)
            is GenericMessage.Content.Edited -> {
                val replacingMessageId = protoContent.value.replacingMessageId
                when (val editContent = protoContent.value.content) {
                    is MessageEdit.Content.Text -> {
                        MessageContent.TextEdited(replacingMessageId, editContent.value.content)
                    }
                    //TODO: for now we do not implement it
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
                    MessageContent.DeleteForMe(hiddenMessage.messageId, hiddenMessage.conversationId, hiddenMessage.qualifiedConversationId)
                } else {
                    kaliumLogger.w("Hidden message is null. Message UUID = $genericMessage.")
                    MessageContent.Ignored
                }
            }

            is GenericMessage.Content.Knock -> MessageContent.Ignored
            is GenericMessage.Content.LastRead -> MessageContent.Ignored
            is GenericMessage.Content.Location -> MessageContent.Unknown(typeName, encodedContent.data)
            is GenericMessage.Content.Reaction -> MessageContent.Ignored
            else -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = $genericMessage.")
                MessageContent.Ignored
            }
        }
        return readableContent
    }
}
