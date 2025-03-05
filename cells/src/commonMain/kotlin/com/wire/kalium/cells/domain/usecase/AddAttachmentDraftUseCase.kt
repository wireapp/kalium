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

import com.benasher44.uuid.uuid4
import com.wire.kalium.cells.domain.CellUploadEvent
import com.wire.kalium.cells.domain.CellUploadManager
import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.FAILED
import com.wire.kalium.cells.domain.model.AttachmentUploadStatus.UPLOADED
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
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
    private val conversationDao: ConversationDAO,
    private val repository: MessageAttachmentDraftRepository,
    private val scope: CoroutineScope,
) : AddAttachmentDraftUseCase {

    override suspend fun invoke(
        conversationId: QualifiedID,
        fileName: String,
        mimeType: String,
        assetPath: Path,
        assetSize: Long,
        assetMetadata: AssetContent.AssetMetadata?,
    ): Either<CoreFailure, Unit> {

        val cellName = conversationDao.getCellName(QualifiedIDEntity(conversationId.value, conversationId.domain))

        return if (cellName != null) {
            uploadManager.upload(assetPath, assetSize, "$cellName/$fileName").map { node ->
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
            }.map {}
        } else {
            persistDraftNode(
                conversationId = conversationId,
                assetPath = assetPath,
                node = CellNode(
                    uuid = uuid4().toString(),
                    versionId = "",
                    path = fileName,
                    size = assetSize,
                ),
                mimeType = mimeType,
                metadata = assetMetadata,
            )
        }
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
