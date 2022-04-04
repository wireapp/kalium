package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.sync.SyncManager

class ConversationScope(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {
    val getConversations: GetConversationsUseCase
        get() = GetConversationsUseCase(conversationRepository, syncManager)

    val getConversationDetails: GetConversationDetailsUseCase
        get() = GetConversationDetailsUseCase(conversationRepository, syncManager)

    val observeConversationDetails: ObserveConversationDetailsUseCase
        get() = ObserveConversationDetailsUseCase(conversationRepository, syncManager)

    val syncConversations: SyncConversationsUseCase
        get() = SyncConversationsUseCase(conversationRepository)
}
