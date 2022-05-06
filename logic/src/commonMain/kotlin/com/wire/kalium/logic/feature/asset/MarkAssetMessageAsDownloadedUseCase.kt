package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId

interface MarkAssetMessageAsDownloadedUseCase {
    suspend operator fun invoke(conversationId: ConversationId, messageId: String): Result
}

class MarkAssetMessageAsDownloadedUseCaseImpl(private val conversationRepository: ConversationRepository) : MarkAssetMessageAsDownloadedUseCase {

    override suspend operator fun invoke(conversationId: ConversationId, messageId: String): Result {
        return conversationRepository.updateConversationNotificationDate(conversationId, date)
        .fold({ Result.Failure(it) }) { Result.Success }
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val coreFailure: CoreFailure) : Result()
}
