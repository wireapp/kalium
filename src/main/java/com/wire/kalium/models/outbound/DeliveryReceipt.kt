package com.wire.kalium.models.outbound

import com.waz.model.Messages.Confirmation
import com.waz.model.Messages.GenericMessage
import java.util.*

class DeliveryReceipt(private val firstMessageId: UUID?) : GenericMessageIdentifiable {
    override val messageId: UUID = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage {
        val confirmation = Confirmation.newBuilder()
            .setFirstMessageId(firstMessageId.toString())
            .setType(Confirmation.Type.DELIVERED)
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setConfirmation(confirmation)
            .build()
    }
}
