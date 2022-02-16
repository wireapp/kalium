package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.persistence.dao.QualifiedID
import com.wire.kalium.persistence.dao.message.Message
import kotlinx.coroutines.flow.Flow

class GetRecentMessagesUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: QualifiedID, limit: Int = 100): Flow<List<Message>> {
        return messageRepository.getMessagesForConversation(conversationId, limit)
    }
}
