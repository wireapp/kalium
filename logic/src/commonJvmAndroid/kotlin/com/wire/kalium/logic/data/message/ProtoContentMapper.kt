package com.wire.kalium.logic.data.message

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage

actual class ProtoContentMapper {

    actual fun encodeToProtobuf(messageUid: String, messageContent: MessageContent): PlainMessageBlob {
        val builder = GenericMessage.newBuilder()
            .setMessageId(messageUid)

        when (messageContent) {
            is MessageContent.Text -> {
                val text = Messages.Text.newBuilder()
                    .setContent(messageContent.value)
                    .build()
                builder.text = text
            }
            else -> {
                throw IllegalArgumentException("Unexpected message content type: $messageContent")
            }
        }
        return PlainMessageBlob(builder.build().toByteArray())
    }

    actual fun decodeFromProtobuf(encodedContent: PlainMessageBlob): MessageContent {
        val genericMessage = GenericMessage.parseFrom(encodedContent.data)

        //TODO Handle other message types
        return if (genericMessage.hasText()) {
            MessageContent.Text(genericMessage.text.content)
        } else {
            MessageContent.Unknown
        }
    }
}
