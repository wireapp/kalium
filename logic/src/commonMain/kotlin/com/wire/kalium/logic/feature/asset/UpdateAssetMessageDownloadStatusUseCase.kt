package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message.DownloadStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

interface UpdateAssetMessageDownloadStatusUseCase {
    suspend operator fun invoke(downloadStatus: DownloadStatus, conversationId: ConversationId, messageId: String): UpdateDownloadStatusResult
}

class UpdateAssetMessageDownloadStatusUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageDownloadStatusUseCase {

    override suspend operator fun invoke(downloadStatus: DownloadStatus, conversationId: ConversationId, messageId: String): UpdateDownloadStatusResult {
        return messageRepository.updateAssetMessageDownloadStatus(downloadStatus, conversationId, messageId).fold({
            UpdateDownloadStatusResult.Failure(it)
        }, {
            UpdateDownloadStatusResult.Success
        })
    }
}

sealed class UpdateDownloadStatusResult {
    object Success : UpdateDownloadStatusResult()
    data class Failure(val coreFailure: CoreFailure) : UpdateDownloadStatusResult()
}
