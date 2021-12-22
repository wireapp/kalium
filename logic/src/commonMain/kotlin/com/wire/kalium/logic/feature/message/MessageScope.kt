package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository

class MessageScope(
    private val messageRepository: MessageRepository
    // cryptography things
) {

    val sendTextMessage: SendTextMessageUseCase get() = SendTextMessageUseCase(messageRepository)
}
