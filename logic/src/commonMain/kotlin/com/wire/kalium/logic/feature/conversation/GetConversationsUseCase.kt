package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class GetConversationsUseCase(private val conversationRepository: ConversationRepository) {

    suspend operator fun invoke(): Flow<List<Conversation>> = flow {
        emit(conversationRepository.getConversationList())
    }
}
