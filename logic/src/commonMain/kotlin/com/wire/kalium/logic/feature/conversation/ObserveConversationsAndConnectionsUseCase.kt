package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.feature.connection.ObserveConnectionListUseCase
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

fun interface ObserveConversationsAndConnectionsUseCase {
    /**
     * Convenience UseCase that allows to get conversations and connections merged and desc sorted by date
     * Using this will allow clients not handling specific sorting or merging logic
     */
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConversationsAndConnectionsUseCaseImpl(
    private val syncManager: SyncManager,
    private val observeConversationListDetailsUseCase: ObserveConversationListDetailsUseCase,
    private val observeConnectionListUseCase: ObserveConnectionListUseCase
) : ObserveConversationsAndConnectionsUseCase {
    override suspend fun invoke(): Flow<List<ConversationDetails>> {
        syncManager.startSyncIfIdle()
        return combine(observeConversationListDetailsUseCase(), observeConnectionListUseCase()) { conversations, connections ->
            (conversations + connections).sortedByDescending { it.conversation.lastModifiedDate }
        }
    }
}
