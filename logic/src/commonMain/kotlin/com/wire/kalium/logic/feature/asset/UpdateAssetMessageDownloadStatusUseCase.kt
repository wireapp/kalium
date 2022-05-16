package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message.DownloadStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

interface UpdateAssetMessageDownloadStatusUseCase {
    /**
     * Function that allows update an asset message download status. This field indicates whether the asset has been downloaded locally already or not.
     *
     * @param downloadStatus the new download status to update the asset message
     * @param conversationId the conversation identifier
     * @param messageId the message identifier
     * @return [UpdateDownloadStatusResult] sealed class with either a Success state in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        downloadStatus: DownloadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateDownloadStatusResult
}

class UpdateAssetMessageDownloadStatusUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageDownloadStatusUseCase {

    override suspend operator fun invoke(
        downloadStatus: DownloadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateDownloadStatusResult {
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
