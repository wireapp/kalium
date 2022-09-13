package com.wire.kalium.logic.feature.asset

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message.UploadStatus
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.fold

interface UpdateAssetMessageUploadStatusUseCase {
    /**
     * Function that allows update an asset message upload status. This field indicates whether the asset has been already uploaded remotely
     * or not.
     *
     * @param uploadStatus the new upload status to update the asset message
     * @param conversationId the conversation identifier
     * @param messageId the message identifier
     * @return [UpdateUploadStatusResult] sealed class with either a Success state in case of success or [CoreFailure] on failure
     */
    suspend operator fun invoke(
        uploadStatus: UploadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateUploadStatusResult
}

class UpdateAssetMessageUploadStatusUseCaseImpl(
    private val messageRepository: MessageRepository
) : UpdateAssetMessageUploadStatusUseCase {

    override suspend operator fun invoke(
        uploadStatus: UploadStatus,
        conversationId: ConversationId,
        messageId: String
    ): UpdateUploadStatusResult {
        return messageRepository.updateAssetMessageUploadStatus(uploadStatus, conversationId, messageId).fold({
            UpdateUploadStatusResult.Failure(it)
        }, {
            UpdateUploadStatusResult.Success
        })
    }
}

sealed class UpdateUploadStatusResult {
    object Success : UpdateUploadStatusResult()
    data class Failure(val coreFailure: CoreFailure) : UpdateUploadStatusResult()
}
