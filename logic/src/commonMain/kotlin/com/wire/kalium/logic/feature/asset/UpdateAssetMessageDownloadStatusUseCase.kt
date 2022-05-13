package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message.DownloadStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

interface UpdateAssetMessageDownloadStatusUseCase {
    suspend operator fun invoke(downloadStatus: DownloadStatus, conversationId: ConversationId, messageId: String): Result
}

class UpdateAssetMessageDownloadStatusUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageDownloadStatusUseCase {

    override suspend operator fun invoke(downloadStatus: DownloadStatus, conversationId: ConversationId, messageId: String): Result {
        return messageRepository.updateAssetMessageDownloadStatus(downloadStatus, conversationId, messageId).fold({
            Result.Failure(it)
        }, {
            Result.Success
        })
    }
}

sealed class Result {
    object Success : Result()
    data class Failure(val coreFailure: CoreFailure) : Result()
}
