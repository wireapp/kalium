package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.message.MessageRecord

class SendTextMessageUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, text: String) {
        //TODO
        val message = MessageRecord(
            id = "someUUID",
            content = text,
            conversationId = conversationId,
            date = "25 Jan 2022 13:30:00 GMT",
            senderUserId = QualifiedID("sender_id", "domain"),
            senderClientId = "someSenderClientId",
            status = MessageRecord.Status.READ
        )

        messageRepository.persistMessage(message)
    }
}
