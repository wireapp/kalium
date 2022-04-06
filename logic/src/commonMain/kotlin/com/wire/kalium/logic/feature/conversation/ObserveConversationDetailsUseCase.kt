package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class ObserveConversationDetailsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(conversationId: ConversationId): Flow<ConversationDetails> {
        syncManager.waitForSlowSyncToComplete()
        return conversationRepository.getConversationDetailsById(conversationId)
    }
}
