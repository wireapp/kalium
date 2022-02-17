package com.wire.kalium.logic.data.message

import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.persistence.dao.message.MessageRecord

interface MessageMapper {
    fun fromMessageToRecord(message: Message): MessageRecord
    fun fromRecordToMessage(message: MessageRecord): Message
}

class MessageMapperImpl(private val idMapper: IdMapper) : MessageMapper {
    override fun fromMessageToRecord(message: Message): MessageRecord {
        val stringContent = when (val content = message.content) {
            is MessageContent.Text -> content.value
            MessageContent.Unknown -> null
        }
        val status = when (message.status) {
            Message.Status.PENDING -> MessageRecord.Status.PENDING
            Message.Status.SENT -> MessageRecord.Status.SENT
            Message.Status.READ -> MessageRecord.Status.READ
            Message.Status.FAILED -> MessageRecord.Status.FAILED
        }
        return MessageRecord(
            message.id,
            stringContent,
            idMapper.toDaoModel(message.conversationId),
            message.date,
            idMapper.toDaoModel(message.senderUserId),
            message.senderClientId.value,
            status
        )
    }

    override fun fromRecordToMessage(message: MessageRecord): Message {
        val content = when (val stringContent = message.content) {
            null -> MessageContent.Unknown
            else -> MessageContent.Text(stringContent)
        }
        val status = when (message.status) {
            MessageRecord.Status.PENDING -> Message.Status.PENDING
            MessageRecord.Status.SENT -> Message.Status.SENT
            MessageRecord.Status.READ -> Message.Status.READ
            MessageRecord.Status.FAILED -> Message.Status.FAILED
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
