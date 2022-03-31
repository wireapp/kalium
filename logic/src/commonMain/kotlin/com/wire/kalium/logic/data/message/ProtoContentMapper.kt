package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.protobuf.decodeFromByteArray
import com.wire.kalium.protobuf.encodeToByteArray
import com.wire.kalium.protobuf.messages.Calling
import com.wire.kalium.protobuf.messages.GenericMessage
import com.wire.kalium.protobuf.messages.MessageDelete
import com.wire.kalium.protobuf.messages.MessageHide
import com.wire.kalium.protobuf.messages.QualifiedConversationId
import com.wire.kalium.protobuf.messages.Text

interface ProtoContentMapper {
    fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob
    fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent
}

class ProtoContentMapperImpl : ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        val (messageUid, messageContent) = protoContent

        val content = when (messageContent) {
            is MessageContent.Text -> {
                GenericMessage.Content.Text(Text(content = messageContent.value))
            }
            is MessageContent.Calling -> {
                GenericMessage.Content.Calling(calling = Calling(content = messageContent.value))
            }
            is MessageContent.DeleteMessage -> {
                GenericMessage.Content.Deleted(MessageDelete(messageId = messageContent.messageId))
            }
            is MessageContent.DeleteForMe -> {
                val qualifiedConversationId =
                    QualifiedConversationId(id = messageContent.conversationId.value, domain = messageContent.conversationId.domain)
                GenericMessage.Content.Hidden(
                    MessageHide(
                        conversationId = messageContent.conversationId.value,
                        messageId = messageContent.messageId,
                        qualifiedConversationId = qualifiedConversationId
                    )
                )
            }
            else -> {
                throw IllegalArgumentException("Unexpected message content type: $messageContent")
            }
        }

        val message = GenericMessage(messageUid, content)
        return PlainMessageBlob(message.encodeToByteArray())
    }

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.decodeFromByteArray(encodedContent.data)

        kaliumLogger.d("Received message $genericMessage")
        val content = when (val protoContent = genericMessage.content) {
            is GenericMessage.Content.Text -> MessageContent.Text(protoContent.value.content)
            is GenericMessage.Content.Asset -> MessageContent.Unknown
            is GenericMessage.Content.Availability -> MessageContent.Unknown
            is GenericMessage.Content.ButtonAction -> MessageContent.Unknown
            is GenericMessage.Content.ButtonActionConfirmation -> MessageContent.Unknown
            is GenericMessage.Content.Calling -> MessageContent.Calling(value = protoContent.value.content)
            is GenericMessage.Content.Cleared -> MessageContent.Unknown
            is GenericMessage.Content.ClientAction -> MessageContent.Unknown
            is GenericMessage.Content.Composite -> MessageContent.Unknown
            is GenericMessage.Content.Confirmation -> MessageContent.Unknown
            is GenericMessage.Content.DataTransfer -> MessageContent.Unknown
            is GenericMessage.Content.Deleted -> MessageContent.DeleteMessage(genericMessage.messageId)
            is GenericMessage.Content.Edited -> MessageContent.Unknown
            is GenericMessage.Content.Ephemeral -> MessageContent.Unknown
            is GenericMessage.Content.External -> MessageContent.Unknown
            is GenericMessage.Content.Hidden -> {
                val hiddenMessage = genericMessage.hidden
                if (hiddenMessage != null) {
                    MessageContent.DeleteForMe(
                        hiddenMessage.messageId,
                        ConversationId(
                            hiddenMessage.qualifiedConversationId!!.id,
                            hiddenMessage.qualifiedConversationId!!.domain
                        )
                    )
                } else {
                    kaliumLogger.w("Hidden message is null. Message UUID = $genericMessage.")
                    MessageContent.Unknown
                }
            }
            is GenericMessage.Content.Image -> MessageContent.Unknown
            is GenericMessage.Content.Knock -> MessageContent.Unknown
            is GenericMessage.Content.LastRead -> MessageContent.Unknown
            is GenericMessage.Content.Location -> MessageContent.Unknown
            is GenericMessage.Content.Reaction -> MessageContent.Unknown
            null -> {
                kaliumLogger.w("Null content when parsing protobuf. Message UUID = $genericMessage.")
                MessageContent.Unknown
            }
        }

        return ProtoContent(genericMessage.messageId, content)
    }
}
