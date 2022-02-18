package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetConversationDetailsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(conversationId: QualifiedID): Flow<Conversation> {
        syncManager.waitForSlowSyncToComplete()
        return conversationRepository.getConversationDetails(conversationId)
    }
}
