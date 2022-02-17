package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.user.UserId

class SendTextMessageUseCase(
    private val messageRepository: MessageRepository,
    private val userId: UserId,
    private val clientRepository: ClientRepository
) {

    suspend operator fun invoke(conversationId: ConversationId, text: String) {
        val message = Message(
            id = "someUUID",
            content = MessageContent.Text(text),
            conversationId = conversationId,
            date = "25 Jan 2022 13:30:00 GMT",
            senderUserId = userId,
            senderClientId = clientRepository.currentClientId().fold({ TODO("Fix me") }, { it }),
            status = Message.Status.PENDING
        )

        messageRepository.persistMessage(message)
    }
}
