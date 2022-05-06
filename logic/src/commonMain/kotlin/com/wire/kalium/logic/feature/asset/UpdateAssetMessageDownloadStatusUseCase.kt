package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository

interface UpdateAssetMessageDownloadStatusUseCase {
    suspend operator fun invoke(downloadStatus: Message.DownloadStatus, conversationId: ConversationId, messageId: String): Result
}

class MarkAssetMessageAsDownloadedUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageDownloadStatusUseCase {

    override suspend operator fun invoke(downloadStatus: Message.DownloadStatus, conversationId: ConversationId, messageId: String): Result {
        return messageRepository.updateAssetMessageDownloadStatus(downloadStatus, conversationId, messageId).fold({
            Result.Failure(it)
        },{
            Result.Success
        })
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val coreFailure: CoreFailure) : Result()
}
