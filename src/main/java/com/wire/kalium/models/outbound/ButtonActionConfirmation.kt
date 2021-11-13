package com.wire.kalium.models.outbound

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage
import java.util.*

class ButtonActionConfirmation(private val refMsgId: UUID?, private val buttonId: String?) : GenericMessageIdentifiable {
    override val messageId: UUID = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage {
        val confirmation = Messages.ButtonActionConfirmation.newBuilder()
            .setButtonId(buttonId)
            .setReferenceMessageId(refMsgId.toString())
        return GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
            .setButtonActionConfirmation(confirmation)
            .build()
    }
}
