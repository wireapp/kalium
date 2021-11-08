package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages

class MessageDelete(private val delMessageId: UUID?) : IGeneric {
    private val messageId = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage? {
        val del = Messages.MessageDelete.newBuilder()
            .setMessageId(delMessageId.toString())
        return GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
            .setDeleted(del)
            .build()
    }

    override fun getMessageId(): UUID? {
        return messageId
    }
}
