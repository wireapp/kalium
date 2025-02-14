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

import com.wire.kalium.cells.CellsScope.Companion.ROOT_CELL
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.FAILED
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.UPLOADED
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.QualifiedID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import okio.Path

public interface AddAttachmentDraftUseCase {
    /**
     * Adds an attachment draft to the conversation and starts attachment upload.
     *
     * @param conversationId ID of the conversation
     * @param fileName name of the attachment
     * @param assetPath path to the attachment asset
     * @param assetSize size of the attachment asset
     * @return [Either] with [Unit] or [NetworkFailure]
     */
    public suspend operator fun invoke(
        conversationId: QualifiedID,
        fileName: String,
        assetPath: Path,
        assetSize: Long,
    ): Either<NetworkFailure, Unit>
}

internal class AddAttachmentDraftUseCaseImpl internal constructor(
    private val uploadManager: CellUploadManager,
    private val repository: MessageAttachmentDraftRepository,
    private val scope: CoroutineScope,
) : AddAttachmentDraftUseCase {

    override suspend fun invoke(
        conversationId: QualifiedID,
        fileName: String,
        assetPath: Path,
        assetSize: Long,
    ): Either<NetworkFailure, Unit> {

        val destNodePath = "$ROOT_CELL/$conversationId/$fileName"

        return uploadManager.upload(assetPath, assetSize, destNodePath).map { node ->
            persistDraftNode(conversationId, assetPath, node).onSuccess {
                scope.launch observer@{
                    uploadManager.observeUpload(node.uuid)?.collectLatest { event ->
                        when (event) {
                            CellUploadEvent.UploadCompleted -> repository.updateStatus(node.uuid, UPLOADED)
                            CellUploadEvent.UploadError -> repository.updateStatus(node.uuid, FAILED)
                            CellUploadEvent.UploadCancelled -> this@observer.cancel()
                            is CellUploadEvent.UploadProgress -> {}
                        }
                    }
                }
            }
        }
    }

    private suspend fun persistDraftNode(conversationId: QualifiedID, assetPath: Path, node: CellNode) =
        repository.add(
            conversationId = conversationId,
            node = node,
            dataPath = assetPath.toString(),
        )
}
