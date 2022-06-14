package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.persistence.dao.message.MessageEntity
import kotlinx.coroutines.flow.Flow

class GetRecentMessagesUseCase(private val messageRepository: MessageRepository) {

    suspend operator fun invoke(
        conversationId: ConversationId,
        limit: Int = 100,
        offset: Int = 0,
        visibility: List<Message.Visibility> = Message.Visibility.values().toList()
    ): Flow<List<Message>> = messageRepository.getMessagesByConversationIdAndVisibility(conversationId, limit, offset, visibility)
}
