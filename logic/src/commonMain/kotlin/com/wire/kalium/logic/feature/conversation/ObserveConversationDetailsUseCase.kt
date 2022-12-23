package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * This use case will observe and return the conversation details for a specific conversation.
 * @see ConversationDetails
 */
class ObserveConversationDetailsUseCase(
    private val conversationRepository: ConversationRepository,
) {
    sealed class Result {
        data class Success(val conversationDetails: ConversationDetails) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    /**
     * @param conversationId the id of the conversation to observe
     * @return a flow of [Result] with the [ConversationDetails] of the conversation
     */
    suspend operator fun invoke(conversationId: ConversationId): Flow<Result> {
        return conversationRepository.observeConversationDetailsById(conversationId)
            .map { it.fold({ Result.Failure(it) }, { Result.Success(it) }) }
    }
}
