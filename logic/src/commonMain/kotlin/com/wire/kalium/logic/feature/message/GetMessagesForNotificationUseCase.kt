package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import kotlinx.coroutines.flow.Flow

class GetMessagesForNotificationUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(): Flow<List<Message>> {
        return messageRepository.getMessagesForNotification()
    }
}
