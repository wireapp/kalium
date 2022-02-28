package com.wire.kalium.logic.data.message

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage

class ProtoContentMapperImpl: ProtoContentMapper {

    override fun encodeToProtobuf(protoContent: ProtoContent): PlainMessageBlob {
        val (messageUid, messageContent) = protoContent
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

    override fun decodeFromProtobuf(encodedContent: PlainMessageBlob): ProtoContent {
        val genericMessage = GenericMessage.parseFrom(encodedContent.data)

        //TODO Handle other message types
        val content = if (genericMessage.hasText()) {
            MessageContent.Text(genericMessage.text.content)
        } else {
            MessageContent.Unknown
        }
        return ProtoContent(genericMessage.messageId, content)
    }
}
actual fun provideProtoContentMapper(): ProtoContentMapper = ProtoContentMapperImpl()
