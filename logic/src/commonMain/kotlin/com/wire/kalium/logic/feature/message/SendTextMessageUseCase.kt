package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.message.Message

class SendTextMessageUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, text: String) {
        //TODO
        val message = Message(
            id = "someID",
            content = text,
            conversationId = conversationId,
            date = "25 Jan 2022 13:30:00 GMT",
            senderUserId = QualifiedID("sender_id", "domain"),
            senderClientId = "someSenderClientId",
            status = Message.Status.READ
        )

        messageRepository.persistMessage(message)
    }
}
