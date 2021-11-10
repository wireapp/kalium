package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages

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
