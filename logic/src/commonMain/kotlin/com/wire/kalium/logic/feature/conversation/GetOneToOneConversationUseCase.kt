package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold

class GetOneToOneConversationUseCase internal constructor(
    private val conversationRepository: ConversationRepository
) {

    suspend operator fun invoke(otherUserId: UserId): Result = conversationRepository.getOneToOneConversationWithOtherUser(otherUserId)
        .fold({ Result.Failure }, { Result.Success(it) })

    sealed class Result {
        data class Success(val conversation: Conversation) : Result()
        object Failure : Result()
    }

}
