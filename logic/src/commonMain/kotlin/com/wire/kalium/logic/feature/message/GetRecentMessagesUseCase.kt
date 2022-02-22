package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.conversation.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import kotlinx.coroutines.flow.Flow

class GetRecentMessagesUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(conversationId: ConversationId, limit: Int = 100): Flow<List<Message>> {
        return messageRepository.getMessagesForConversation(conversationId, limit)
    }
}
