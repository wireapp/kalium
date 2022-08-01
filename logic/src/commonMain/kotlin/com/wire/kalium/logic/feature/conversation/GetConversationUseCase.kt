package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GetConversationUseCase(
    private val conversationRepository: ConversationRepository,
    private val syncManager: SyncManager
) {
    sealed class Result {
        data class Success(val conversation: Conversation) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    suspend operator fun invoke(conversationId: QualifiedID): Flow<Result> {
        syncManager.startSyncIfIdle()
        return conversationRepository.observeById(conversationId)
            .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
    }
}
