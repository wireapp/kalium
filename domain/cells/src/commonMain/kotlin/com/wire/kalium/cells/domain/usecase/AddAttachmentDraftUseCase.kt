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

import kotlin.uuid.Uuid
import com.wire.kalium.cells.domain.CellAttachmentDraftUploadResult
import com.wire.kalium.cells.domain.CellAttachmentDraftUploadsImpl
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUploadAttempt
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.FAILED
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.UPLOADED
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    @Suppress("LongParameterList")
    public suspend operator fun invoke(
        conversationId: QualifiedID,
        fileName: String,
        mimeType: String,
        assetPath: Path,
        assetSize: Long,
        assetMetadata: AssetContent.AssetMetadata?,
    ): Either<CoreFailure, Unit>
}

internal class AddAttachmentDraftUseCaseImpl internal constructor(
    private val uploadManager: CellUploadManager,
    private val conversationRepository: CellConversationRepository,
    private val repository: MessageAttachmentDraftRepository,
    private val scope: CoroutineScope,
    private val draftUploads: CellAttachmentDraftUploadsImpl? = null,
) : AddAttachmentDraftUseCase {

    override suspend fun invoke(
        conversationId: QualifiedID,
        fileName: String,
        mimeType: String,
        assetPath: Path,
        assetSize: Long,
        assetMetadata: AssetContent.AssetMetadata?,
    ): Either<CoreFailure, Unit> =
        conversationRepository.getCellName(QualifiedIDEntity(conversationId.value, conversationId.domain)).flatMap { cellName ->
            if (cellName != null && draftUploads?.enabled == true) {
                addTrackedDraft(conversationId, fileName, mimeType, assetPath, assetSize, assetMetadata, cellName)
            } else if (cellName != null) {
                uploadManager.upload(assetPath, assetSize, "$cellName/$fileName").flatMap { node ->
                    persistDraftNode(conversationId, mimeType, assetPath, node, assetMetadata).onSuccess {
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
            } else {
                // Regular encrypted attachment. No cell upload needed.
                persistDraftNode(
                    conversationId = conversationId,
                    assetPath = assetPath,
                    node = CellNode(
                        uuid = Uuid.random().toString(),
                        versionId = "",
                        path = fileName,
                        size = assetSize,
                    ),
                    mimeType = mimeType,
                    metadata = assetMetadata,
                )
            }
        }

    @Suppress("LongParameterList")
    private suspend fun addTrackedDraft(
        conversationId: QualifiedID,
        fileName: String,
        mimeType: String,
        assetPath: Path,
        assetSize: Long,
        metadata: AssetContent.AssetMetadata?,
        cellName: String,
    ): Either<CoreFailure, Unit> {
        val registered = CompletableDeferred<Either<CoreFailure, Unit>>()
        val operation = checkNotNull(draftUploads).launch {
            var node: CellNode? = null
            var attempt: CellUploadAttempt? = null
            var statusRecorded = false
            var transferFinished = false
            try {
                val created = uploadManager.upload(assetPath, assetSize, "$cellName/$fileName")
                if (created is Either.Left) {
                    registered.complete(created)
                    CellAttachmentDraftUploadResult.Failed
                } else {
                    val createdNode = (created as Either.Right).value
                    node = createdNode
                    attempt = uploadManager.captureUploadAttempt(createdNode.uuid)
                    val persisted = persistDraftNode(conversationId, mimeType, assetPath, createdNode, metadata)
                    registered.complete(persisted)
                    if (persisted is Either.Left) {
                        CellAttachmentDraftUploadResult.Failed
                    } else {
                        val result = uploadManager.awaitDraftTransfer(createdNode.uuid)
                        transferFinished = result != CellAttachmentDraftUploadResult.Cancelled
                        val status = if (result == CellAttachmentDraftUploadResult.Completed) UPLOADED else FAILED
                        val updated = repository.updateStatus(createdNode.uuid, status)
                        statusRecorded = updated is Either.Right
                        if (statusRecorded) result else CellAttachmentDraftUploadResult.Failed
                    }
                }
            } finally {
                withContext(NonCancellable) {
                    node?.let { created ->
                        // Also covers a node created after native cancellation began, before persistence returned.
                        if (transferFinished) attempt?.awaitStopped() else attempt?.cancelAndAwait()
                        if (!statusRecorded) repository.updateStatus(created.uuid, FAILED)
                    }
                }
            }
        }
        // Cancellation of the originating ViewModel's await does not cancel this session-owned operation.
        operation.job.invokeOnCompletion { registered.complete(CoreFailure.Unknown(null).left()) }
        return registered.await()
    }

    private suspend fun persistDraftNode(
        conversationId: QualifiedID,
        mimeType: String,
        assetPath: Path,
        node: CellNode,
        metadata: AssetContent.AssetMetadata?,
    ) = repository.add(
            conversationId = conversationId,
            node = node,
            mimeType = mimeType,
            dataPath = assetPath.toString(),
            metadata = metadata,
        )
}
