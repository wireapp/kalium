package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

class ObserveConversationListDetailsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {

    suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        syncManager.startSyncIfIdle()
        return conversationRepository.observeConversationList().map { conversations ->
            conversations.map { conversation ->
                conversationRepository.observeConversationDetailsById(conversation.id)
            }
        }.flatMapLatest { flowsOfDetails ->
            combine(flowsOfDetails) { latestValues -> latestValues.asList() }
        }
    }
}
