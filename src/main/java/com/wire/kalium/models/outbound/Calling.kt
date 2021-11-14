package com.wire.kalium.models.outbound

import com.waz.model.Messages
import com.waz.model.Messages.GenericMessage
import java.util.*

class Calling(private val content: String?) : GenericMessageIdentifiable {
    override val messageId: UUID = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage {
        val ret = GenericMessage.newBuilder()
            .setMessageId(messageId.toString())
        val calling = Messages.Calling.newBuilder()
            .setContent(content)
        return ret
            .setCalling(calling)
            .build()
    }
}
