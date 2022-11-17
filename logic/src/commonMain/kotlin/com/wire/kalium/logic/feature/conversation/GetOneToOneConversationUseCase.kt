package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

class GetOneToOneConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) {

    suspend operator fun invoke(otherUserId: UserId): Flow<Result> =
        conversationRepository.observeOneToOneConversationWithOtherUser(otherUserId)
            .map { result -> result.fold({ Result.Failure }, { Result.Success(it) }) }
            .flowOn(dispatchers.io)

    sealed class Result {
        data class Success(val conversation: Conversation) : Result()
        object Failure : Result()
    }

}
