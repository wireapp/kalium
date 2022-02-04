package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.sync.SyncManager

class ConversationScope(
    conversationRepository: ConversationRepository,
    syncManager: SyncManager
) {
    val getConversations: GetConversationsUseCase = GetConversationsUseCase(conversationRepository, syncManager)
    val syncConversations: SyncConversationsUseCase = SyncConversationsUseCase(conversationRepository)
}
