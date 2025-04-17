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

import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.FAILED
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.UPLOADED
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.UPLOADING
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

public interface RetryAttachmentUploadUseCase {
    /**
     * Retry attachment upload.
     *
     * @param attachmentUuid UUID of the attachment
     * @return [Either] with [Unit] or [CoreFailure]
     */
    @Suppress("LongParameterList")
    public suspend operator fun invoke(attachmentUuid: String): Either<CoreFailure, Unit>
}

internal class RetryAttachmentUploadUseCaseImpl internal constructor(
    private val uploadManager: CellUploadManager,
    private val repository: MessageAttachmentDraftRepository,
    private val scope: CoroutineScope,
) : RetryAttachmentUploadUseCase {

    override suspend fun invoke(attachmentUuid: String) = repository.updateStatus(attachmentUuid, UPLOADING)
        .onSuccess {
            uploadManager.retryUpload(attachmentUuid)
            scope.launch observer@{
                uploadManager.observeUpload(attachmentUuid)?.collectLatest { event ->
                    when (event) {
                        CellUploadEvent.UploadCompleted -> repository.updateStatus(attachmentUuid, UPLOADED)
                        CellUploadEvent.UploadError -> repository.updateStatus(attachmentUuid, FAILED)
                        CellUploadEvent.UploadCancelled -> this@observer.cancel()
                        is CellUploadEvent.UploadProgress -> {}
                    }
                }
            }
        }
}
