package com.wire.kalium.assets

import java.util.UUID
import com.waz.model.Messages.GenericMessage
import com.waz.model.Messages

class Calling(private val content: String?) : IGeneric {
    private val messageId = UUID.randomUUID()
    override fun createGenericMsg(): GenericMessage? {
        val ret = GenericMessage.newBuilder()
            .setMessageId(getMessageId().toString())
        val calling = Messages.Calling.newBuilder()
            .setContent(content)
        return ret
            .setCalling(calling)
            .build()
    }

    override fun getMessageId(): UUID? {
        return messageId
    }
}
