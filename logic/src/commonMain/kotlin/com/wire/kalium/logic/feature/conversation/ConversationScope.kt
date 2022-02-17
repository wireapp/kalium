package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.sync.SyncManager

class ConversationScope(
    conversationRepository: ConversationRepository,
    syncManager: SyncManager
) {
    // TODO: get()
    val getConversations: GetConversationsUseCase = GetConversationsUseCase(conversationRepository, syncManager)
    val getConversationDetails: GetConversationDetailsUseCase = GetConversationDetailsUseCase(conversationRepository, syncManager)
    val syncConversations: SyncConversationsUseCase = SyncConversationsUseCase(conversationRepository)
}
