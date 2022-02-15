package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.message.Message

class SendTextMessageUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, text: String) {
        //TODO
        val message = Message(
            id = QualifiedID("message_id", "domain"),
            content = text,
            conversationId = conversationId,
            timestamp = 100000L,
            senderId = QualifiedID("sender_id", "domain"),
            status = "status"
        )

        messageRepository.persistMessage(message)
    }
}
