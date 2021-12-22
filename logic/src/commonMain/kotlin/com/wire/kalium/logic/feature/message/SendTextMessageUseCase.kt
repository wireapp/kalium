package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.network.api.ConversationId

class SendTextMessageUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: ConversationId, text: String) {
        TODO("Send a message!")
    }
}
