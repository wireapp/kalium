package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.Either
import kotlinx.coroutines.flow.Flow

interface MarkMessagesAsNotifiedUseCase {
    suspend operator fun invoke(conversationId: ConversationId?, date: String): Result
}

class MarkMessagesAsNotifiedUseCaseImpl(private val conversationRepository: ConversationRepository) : MarkMessagesAsNotifiedUseCase {

    override suspend operator fun invoke(conversationId: ConversationId?, date: String): Result {
        return if (conversationId == null) {
            conversationRepository.setAllConversationsAsNotified(date)
        } else {
            conversationRepository.setConversationAsNotified(conversationId, date)
        }
            .fold({ Result.Failure(it) }) { Result.Success }
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
