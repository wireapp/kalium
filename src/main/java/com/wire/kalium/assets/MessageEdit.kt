package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages

class MessageEdit(private val replacingMessageId: UUID?, private val text: String?) : IGeneric {
    private val messageId = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage? {
        val text = Messages.Text.newBuilder()
            .setContent(text)
        val messageEdit = Messages.MessageEdit.newBuilder()
            .setReplacingMessageId(replacingMessageId.toString())
            .setText(text)
        return GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
            .setEdited(messageEdit)
            .build()
    }

    override fun getMessageId(): UUID? {
        return messageId
    }
}
