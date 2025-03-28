/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.onSuccess

public interface RemoveAttachmentDraftUseCase {
    /**
     * Removes the draft attachment from conversation.
     * If the attachment is in the process of uploading, the upload will be cancelled.
     * If the attachment is already uploaded, the attachment draft will be removed from the server.
     *
     * @param uuid UUID of the attachment
     * @return [Either] with [Unit] or [CoreFailure]
     */
    public suspend operator fun invoke(uuid: String): Either<CoreFailure, Unit>
}

internal class RemoveAttachmentDraftUseCaseImpl internal constructor(
    private val uploadManager: CellUploadManager,
    private val attachmentsRepository: MessageAttachmentDraftRepository,
    private val cellsRepository: CellsRepository,
) : RemoveAttachmentDraftUseCase {

    override suspend fun invoke(uuid: String): Either<CoreFailure, Unit> =
        attachmentsRepository.get(uuid).flatMap { attachment ->
            when (attachment?.uploadStatus) {
                AttachmentUploadStatus.UPLOADING -> cancelAttachmentUpload(uuid)
                AttachmentUploadStatus.UPLOADED -> removeAttachmentDraft(attachment)
                AttachmentUploadStatus.FAILED -> attachmentsRepository.remove(uuid)
                null -> StorageFailure.DataNotFound.left()
            }
        }

    private suspend fun cancelAttachmentUpload(uuid: String): Either<CoreFailure, Unit> {
        uploadManager.cancelUpload(uuid)
        return attachmentsRepository.remove(uuid)
    }

    private suspend fun removeAttachmentDraft(attachment: AttachmentDraft) =
        cellsRepository.cancelDraft(attachment.uuid, attachment.versionId).onSuccess {
            attachmentsRepository.remove(attachment.uuid)
        }
}
