package com.wire.kalium.logic.data.message

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage
import com.wire.kalium.logic.data.id.ConversationId

class ProtoContentMapperImpl : ProtoContentMapper {

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
            is MessageContent.DeleteMessage -> {
                val deleted = Messages.MessageDelete.newBuilder()
                    .setMessageId(messageContent.messageId)
                    .build()
                builder.deleted = deleted
            }
            is MessageContent.HideMessage -> {
                val qualifiedConversationId = Messages.QualifiedConversationId.newBuilder()
                    .setId(messageContent.conversationId.value)
                    .setDomain(messageContent.conversationId.domain).build()
                val hidden = Messages.MessageHide.newBuilder()
                    .setMessageId(messageContent.messageId)
                    //based on the documentation this conversation id is deprecated, but somehow it crashed when not passing it!
                    .setConversationId(messageContent.conversationId.value)
                    .setQualifiedConversationId(qualifiedConversationId)
                    .build()
                builder.hidden = hidden
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
        val content = when {
            genericMessage.hasText() -> MessageContent.Text(genericMessage.text.content)
            genericMessage.hasDeleted() -> MessageContent.DeleteMessage(genericMessage.messageId)
            genericMessage.hasHidden() -> MessageContent.HideMessage(
                genericMessage.messageId,
                ConversationId(genericMessage.hidden.qualifiedConversationId.id, genericMessage.hidden.qualifiedConversationId.domain)
            )
            else -> MessageContent.Unknown
        }
        return ProtoContent(genericMessage.messageId, content)
    }
}
actual fun provideProtoContentMapper(): ProtoContentMapper = ProtoContentMapperImpl()
