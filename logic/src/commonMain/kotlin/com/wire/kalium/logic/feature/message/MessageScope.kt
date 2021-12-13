package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository

class MessageScope(
    private val messageRepository: MessageRepository
) {

    val sendTextMessage: SendTextMessageUseCase get() = SendTextMessageUseCase(messageRepository)
}
