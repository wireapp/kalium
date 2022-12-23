package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.DateTimeUtil

interface MarkMessagesAsNotifiedUseCase {
    suspend operator fun invoke(conversationId: ConversationId?, date: String = DateTimeUtil.currentIsoDateTimeString()): Result
}

class MarkMessagesAsNotifiedUseCaseImpl(private val conversationRepository: ConversationRepository) : MarkMessagesAsNotifiedUseCase {

    override suspend operator fun invoke(conversationId: ConversationId?, date: String): Result {
        return if (conversationId == null) {
            conversationRepository.updateAllConversationsNotificationDate(date)
        } else {
            conversationRepository.updateConversationNotificationDate(conversationId, date)
        }.fold({ Result.Failure(it) }) { Result.Success }
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val storageFailure: StorageFailure) : Result()
}
