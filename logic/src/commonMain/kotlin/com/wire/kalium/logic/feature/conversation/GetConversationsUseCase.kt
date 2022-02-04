package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetConversationsUseCase(private val conversationRepository: ConversationRepository,
                              private val syncManager: SyncManager) {

    suspend operator fun invoke(): Flow<List<Conversation>> {
        syncManager.waitForSlowSyncToComplete()
        return conversationRepository.getConversationList()
    }
}
