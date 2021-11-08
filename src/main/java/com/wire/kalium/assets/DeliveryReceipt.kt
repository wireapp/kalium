package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages.Confirmation

class DeliveryReceipt(private val firstMessageId: UUID?) : IGeneric {
    private val messageId: UUID?
    override fun createGenericMsg(): GenericMessage? {
        val confirmation = Confirmation.newBuilder()
            .setFirstMessageId(firstMessageId.toString())
            .setType(Confirmation.Type.DELIVERED)
        return GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
            .setConfirmation(confirmation)
            .build()
    }

    override fun getMessageId(): UUID? {
        return messageId
    }

    init {
        messageId = UUID.randomUUID()
    }
}
