package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages.Confirmation

class DeliveryReceipt(private val firstMessageId: UUID?) : GenericMessageIdentifiable {
    override val messageId: UUID = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage? {
        val confirmation = Confirmation.newBuilder()
            .setFirstMessageId(firstMessageId.toString())
            .setType(Confirmation.Type.DELIVERED)
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setConfirmation(confirmation)
            .build()
    }
}
