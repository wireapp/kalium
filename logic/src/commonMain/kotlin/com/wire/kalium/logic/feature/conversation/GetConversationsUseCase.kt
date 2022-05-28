package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class GetConversationsUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {

    sealed class Result {
        data class Success(val convFlow: Flow<List<Conversation>>) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    suspend operator fun invoke(): Result {
        syncManager.startSyncIfIdle()
        return conversationRepository.getConversationList().fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })
    }
}
