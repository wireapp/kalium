package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository

class ConversationScope(
    conversationRepository: ConversationRepository
) {
    val getConversations: GetConversationsUseCase = GetConversationsUseCase(conversationRepository)
}
