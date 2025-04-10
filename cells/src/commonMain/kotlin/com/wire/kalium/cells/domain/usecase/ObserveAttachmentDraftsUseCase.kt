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
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentDraft
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

public interface ObserveAttachmentDraftsUseCase {
    /**
     * Observe draft attachments for the given conversation.
     *
     * @param conversationId ID of the conversation
     * @return [Flow] of [List] of [AttachmentDraft]
     */
    public suspend operator fun invoke(conversationId: QualifiedID): Flow<List<AttachmentDraft>>
}

internal class ObserveAttachmentDraftsUseCaseImpl internal constructor(
    private val repository: MessageAttachmentDraftRepository,
    private val uploadManager: CellUploadManager,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : ObserveAttachmentDraftsUseCase {

    override suspend fun invoke(conversationId: QualifiedID): Flow<List<AttachmentDraft>> {
        removeStaleUploads(conversationId)
        return repository.observe(conversationId)
    }

    /**
     * Remove uploads that are in UPLOADING status but not being uploaded.
     */
    private suspend fun removeStaleUploads(conversationId: QualifiedID) {
        withContext(dispatchers.io) {
            repository.getAll(conversationId).getOrNull()
                ?.filter { it.uploadStatus == AttachmentUploadStatus.UPLOADING }
                ?.filterNot { uploadManager.isUploading(it.uuid) }
                ?.onEach {
                    repository.remove(it.uuid)
                }
        }
    }
}
