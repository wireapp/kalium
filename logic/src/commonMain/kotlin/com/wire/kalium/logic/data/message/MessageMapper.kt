package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.dao.message.MessageEntity

interface MessageMapper {
    fun fromMessageToEntity(message: Message): MessageEntity
    fun fromEntityToMessage(message: MessageEntity): Message
}

class MessageMapperImpl(private val idMapper: IdMapper) : MessageMapper {
    override fun fromMessageToEntity(message: Message): MessageEntity {
        val stringContent = when (val content = message.content) {
            is MessageContent.Text -> content.value
            is MessageContent.Calling -> {
                kaliumLogger.w("fromMessageToEntity - Calling")
                null
            }
            is MessageContent.DeleteMessage -> content.messageId
            is MessageContent.DeleteForMe -> content.messageId
            MessageContent.Unknown -> null
        }
        val status = when (message.status) {
            Message.Status.PENDING -> MessageEntity.Status.PENDING
            Message.Status.SENT -> MessageEntity.Status.SENT
            Message.Status.READ -> MessageEntity.Status.READ
            Message.Status.FAILED -> MessageEntity.Status.FAILED
        }
        return MessageEntity(
            message.id,
            stringContent,
            idMapper.toDaoModel(message.conversationId),
            message.date,
            idMapper.toDaoModel(message.senderUserId),
            message.senderClientId.value,
            status,
            MessageEntity.Visibility.VISIBLE,
            message.shouldNotify
        )
    }

    override fun fromEntityToMessage(message: MessageEntity): Message {
        val content = when (val stringContent = message.content) {
            null -> MessageContent.Unknown
            else -> MessageContent.Text(stringContent)
        }
        val status = when (message.status) {
            MessageEntity.Status.PENDING -> Message.Status.PENDING
            MessageEntity.Status.SENT -> Message.Status.SENT
            MessageEntity.Status.READ -> Message.Status.READ
            MessageEntity.Status.FAILED -> Message.Status.FAILED
        }
        return Message(
            message.id,
            content,
            idMapper.fromDaoModel(message.conversationId),
            message.date,
            idMapper.fromDaoModel(message.senderUserId),
            ClientId(message.senderClientId),
            status
        )
    }
}
