package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.MessageRepository

interface MarkAssetMessageAsDownloadedUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Result
}

class MarkAssetMessageAsDownloadedUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val messageRepository: MessageRepository
) : MarkAssetMessageAsDownloadedUseCase {

    override suspend operator fun invoke(conversationId: ConversationId, messageId: String): Result {
        messageRepository.getMessageById(conversationId, messageId).fold({
            Result.Failure(it)
        },{

        })
        return conversationRepository.updateConversationNotificationDate(conversationId, date)
            .fold({ Result.Failure(it) }) { Result.Success }
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val coreFailure: CoreFailure) : Result()
}
