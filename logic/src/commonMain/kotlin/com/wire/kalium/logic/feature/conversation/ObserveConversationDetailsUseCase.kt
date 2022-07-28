package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ObserveConversationDetailsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {
    sealed class Result {
        data class Success(val conversationDetails: ConversationDetails) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    suspend operator fun invoke(conversationId: ConversationId): Flow<Result> {
        syncManager.startSyncIfIdle()
        return conversationRepository.observeConversationDetailsById(conversationId)
            .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
    }
}
