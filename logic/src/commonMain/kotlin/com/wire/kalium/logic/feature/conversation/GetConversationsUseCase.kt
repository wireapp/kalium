package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * This use case will observe and return the conversation list for the current user.
 * Prefer using [ObserveConversationListDetailsUseCase] instead, since is performance efficient, relying on sql views.
 *
 * @see Conversation
 * @see ObserveConversationDetailsUseCase
 */
class GetConversationsUseCase(
    private val conversationRepository: ConversationRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    sealed class Result {
        data class Success(val convFlow: Flow<List<Conversation>>) : Result()
        data class Failure(val storageFailure: StorageFailure) : Result()
    }

    suspend operator fun invoke(): Result = withContext(dispatcher.default) {
        conversationRepository.getConversationList().fold({
            Result.Failure(it)
        }, {
            Result.Success(it)
        })
    }
}
